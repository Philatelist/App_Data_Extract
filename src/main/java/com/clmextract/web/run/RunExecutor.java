package com.clmextract.web.run;

import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.config.ConfigLoader;
import com.clmextract.csv.FilenameResolver;
import com.clmextract.csv.ManifestCsvWriter;
import com.clmextract.csv.SummaryCsvWriter;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.export.ApiDataSource;
import com.clmextract.export.AttachmentDownloader;
import com.clmextract.export.BoPipeline;
import com.clmextract.export.DataSource;
import com.clmextract.export.OfflineDataSource;
import com.clmextract.export.ReportResult;
import com.clmextract.backup.BackupManager;
import com.clmextract.packaging.ZipPackager;
import com.clmextract.sftp.SftpUploader;
import com.clmextract.web.state.StateStore;
import com.clmextract.web.state.UiState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class RunExecutor {

    private static final Logger LOG = LogManager.getLogger(RunExecutor.class);

    public static final String STEP_EXPORT_CSV = "EXPORT_CSV";
    public static final String STEP_EXPORT_PDF = "EXPORT_PDF";
    public static final String STEP_EXPORT_ATTACHMENTS = "EXPORT_ATTACHMENTS";
    public static final String STEP_PACKAGING = "PACKAGING";
    public static final String STEP_SFTP_UPLOAD = "SFTP_UPLOAD";

    private static final List<String> STEPS = List.of(
        STEP_EXPORT_CSV, STEP_EXPORT_PDF, STEP_EXPORT_ATTACHMENTS, STEP_PACKAGING, STEP_SFTP_UPLOAD
    );

    private final String configPath;
    private final StateStore stateStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "run-executor");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Future<?> currentFuture;

    public RunExecutor(String configPath, StateStore stateStore) {
        this.configPath = configPath;
        this.stateStore = stateStore;
    }

    /**
     * Call once on server startup. If a previous run has no completedAt, the server
     * was restarted mid-run — mark all IN_PROGRESS/PENDING steps as FAILED and
     * move the run to history so the dashboard does not show a phantom active run.
     */
    public void recoverStaleRun() {
        UiState state = stateStore.read();
        UiState.RunState run = state.getCurrentRun();
        if (run == null || run.getCompletedAt() != null) return;

        LOG.warn("Detected incomplete run {} from before restart — marking as interrupted", run.getRunId());
        run.getSteps().replaceAll((step, status) -> {
            if (RunStatus.IN_PROGRESS.name().equals(status) || RunStatus.PENDING.name().equals(status))
                return RunStatus.FAILED.name();
            return status;
        });
        run.setCompletedAt(Instant.now().toString());
        run.getWarnings().add("Run interrupted by server restart");

        List<UiState.RunState> history = new ArrayList<>(state.getRunHistory());
        history.add(0, run);
        if (history.size() > 50) history = history.subList(0, 50);
        state.setRunHistory(new ArrayList<>(history));
        state.setCurrentRun(null);
        stateStore.write(state);
    }

    /** Returns false if already running (caller should 409). */
    public boolean startRun(List<String> selectedBos, String sftpTargetPath, String clmSessionId, DateFilter dateFilter) {
        if (!running.compareAndSet(false, true)) return false;

        String runId = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(
            java.time.LocalDateTime.now());
        String startedAt = Instant.now().toString();

        // Initialize state
        UiState state = stateStore.read();
        UiState.RunState run = new UiState.RunState();
        run.setRunId(runId);
        run.setStartedAt(startedAt);
        run.setCompletedAt(null);
        run.setSelectedBos(new ArrayList<>(selectedBos));
        Map<String, String> steps = new LinkedHashMap<>();
        for (String s : STEPS) steps.put(s, RunStatus.PENDING.name());
        run.setSteps(steps);
        state.setCurrentRun(run);
        state.setSftpTargetPath(sftpTargetPath != null ? sftpTargetPath : "");
        stateStore.write(state);

        currentFuture = executor.submit(() -> executeRun(runId, selectedBos, sftpTargetPath, clmSessionId, dateFilter));
        return true;
    }

    private void executeRun(String runId, List<String> selectedBos, String sftpTargetPath,
                            String clmSessionId, DateFilter dateFilter) {
        DataSource dataSource = null;
        boolean offlineDs = false;
        try {
            // Build DataSource once and reuse across steps
            AppConfig config = ConfigLoader.loadRaw(configPath);
            if (config.isOfflineMode() || clmSessionId == null || clmSessionId.isBlank()) {
                dataSource = new OfflineDataSource("inputs/samples");
                dataSource.login();
                offlineDs = true;
            } else {
                EndpointRegistry registry = new EndpointRegistry(config.getEndpointsFile());
                registry.load();
                ApiDataSource apiDs = new ApiDataSource(config, registry);
                apiDs.injectSessionId(clmSessionId);
                dataSource = apiDs;
                offlineDs = false;
            }
            Path outputDir = Path.of(
                    config.getOutputRoot() != null ? config.getOutputRoot() : "output",
                    config.getExportFolderName() != null ? config.getExportFolderName() : "MetaData",
                    runId);

            // EXPORT_CSV
            updateStep(runId, STEP_EXPORT_CSV, RunStatus.IN_PROGRESS);
            try {
                AppConfig csvConfig = ConfigLoader.loadRaw(configPath);
                Files.createDirectories(outputDir);

                // Resolve BO list: filter by operator selection if non-empty, else use all
                List<BoTypeConfig> boTypes = csvConfig.getBoTypes().stream()
                        .filter(bt -> bt.getName() != null && !bt.getName().isBlank())
                        .filter(bt -> selectedBos.isEmpty() || selectedBos.contains(bt.getName()))
                        .collect(Collectors.toList());

                // Set up summary CSV writer if enabled
                FilenameResolver filenameResolver = new FilenameResolver();
                SummaryCsvWriter summaryCsvWriter = null;
                if (csvConfig.isGenerateSummaryCsv()) {
                    String summaryFilename = filenameResolver.resolve(csvConfig.getSummaryFilenameTemplate(), null, null);
                    summaryCsvWriter = new SummaryCsvWriter(outputDir.resolve(summaryFilename), csvConfig.getDelimiter());
                }

                BoPipeline pipeline = new BoPipeline(csvConfig, dataSource);
                pipeline.setSummaryCsvWriter(summaryCsvWriter);

                List<String> boWarnings = new ArrayList<>();
                int successCount = 0;

                for (BoTypeConfig boTypeConfig : boTypes) {
                    try {
                        pipeline.execute(boTypeConfig, outputDir, dateFilter);
                        successCount++;
                    } catch (Exception e) {
                        String warning = "BO " + boTypeConfig.getName() + " failed: " + e.getMessage();
                        LOG.error("EXPORT_CSV: {}", warning, e);
                        boWarnings.add(warning);
                    }
                }

                // Close summary CSV
                if (summaryCsvWriter != null) {
                    try { summaryCsvWriter.close(); } catch (IOException e) {
                        LOG.warn("Error closing summary CSV: {}", e.getMessage());
                    }
                }

                // Fetch and write reports
                if (!csvConfig.getReports().isEmpty()) {
                    List<ReportResult> reportResults = dataSource.fetchReports(csvConfig.getReports());
                    for (ReportResult result : reportResults) {
                        String filename = filenameResolver.resolve(
                                result.getDisplayName().replaceAll("[^A-Za-z0-9._-]", "_") + "_{DDMMYYYY}_{HHMMSS}.csv",
                                null, null);
                        Path reportFile = outputDir.resolve(filename);
                        try (PrintWriter pw = new PrintWriter(reportFile.toFile())) {
                            pw.print(result.getCsvContent());
                            LOG.info("Written report '{}' to {}", result.getDisplayName(), reportFile);
                        } catch (IOException e) {
                            LOG.warn("Failed to write report '{}': {}", result.getDisplayName(), e.getMessage());
                        }
                    }
                }

                // Generate manifest
                if (csvConfig.isGenerateSummaryCsv()) {
                    try {
                        String manifestFilename = filenameResolver.resolve("Manifest_{DDMMYYYY}_{HHMMSS}.csv", null, null);
                        Path manifestPath = outputDir.resolve(manifestFilename);
                        ManifestCsvWriter manifestCsvWriter = new ManifestCsvWriter(outputDir, manifestPath, csvConfig.getDelimiter());
                        manifestCsvWriter.write();
                        LOG.info("Manifest generated: {}", manifestFilename);
                    } catch (Exception e) {
                        LOG.warn("Manifest generation failed: {}", e.getMessage());
                    }
                }

                // Persist per-BO warnings to RunState
                if (!boWarnings.isEmpty()) {
                    UiState warnState = stateStore.read();
                    if (warnState.getCurrentRun() != null && runId.equals(warnState.getCurrentRun().getRunId())) {
                        warnState.getCurrentRun().getWarnings().addAll(boWarnings);
                        stateStore.write(warnState);
                    }
                }

                if (boTypes.isEmpty() || successCount > 0) {
                    updateStep(runId, STEP_EXPORT_CSV, RunStatus.SUCCESS);
                    // Enforce backup retention after a successful export
                    try {
                        new BackupManager(
                                config.getOutputRoot() != null ? config.getOutputRoot() : "output",
                                config.getExportFolderName() != null ? config.getExportFolderName() : "MetaData",
                                config.getBackupRetentionDays())
                            .enforceRetention();
                    } catch (Exception e) {
                        LOG.warn("BackupManager retention enforcement failed (non-fatal): {}", e.getMessage());
                    }
                } else {
                    updateStep(runId, STEP_EXPORT_CSV, RunStatus.FAILED);
                    failRemaining(runId, STEP_EXPORT_CSV);
                    return;
                }
            } catch (Exception e) {
                LOG.error("EXPORT_CSV failed: {}", e.getMessage(), e);
                updateStep(runId, STEP_EXPORT_CSV, RunStatus.FAILED);
                failRemaining(runId, STEP_EXPORT_CSV);
                return;
            }

            // EXPORT_PDF
            updateStep(runId, STEP_EXPORT_PDF, RunStatus.IN_PROGRESS);
            try {
                AppConfig stepConfig = ConfigLoader.loadRaw(configPath);
                Path stepOutputDir = Path.of(
                        stepConfig.getOutputRoot() != null ? stepConfig.getOutputRoot() : "output",
                        stepConfig.getExportFolderName() != null ? stepConfig.getExportFolderName() : "export");
                Files.createDirectories(stepOutputDir);
                List<Long> trackingIds = resolveTrackingIds(selectedBos, dataSource, stepConfig);
                AttachmentDownloader downloader = new AttachmentDownloader(dataSource, stepOutputDir);
                int count = downloader.downloadPdfs(trackingIds);
                LOG.info("EXPORT_PDF: downloaded {} signed PDF(s)", count);
                updateStep(runId, STEP_EXPORT_PDF, RunStatus.SUCCESS);
            } catch (Exception e) {
                LOG.error("EXPORT_PDF failed: {}", e.getMessage(), e);
                updateStep(runId, STEP_EXPORT_PDF, RunStatus.FAILED);
                failRemaining(runId, STEP_EXPORT_PDF);
                return;
            }

            // EXPORT_ATTACHMENTS
            updateStep(runId, STEP_EXPORT_ATTACHMENTS, RunStatus.IN_PROGRESS);
            try {
                AppConfig stepConfig = ConfigLoader.loadRaw(configPath);
                Path stepOutputDir = Path.of(
                        stepConfig.getOutputRoot() != null ? stepConfig.getOutputRoot() : "output",
                        stepConfig.getExportFolderName() != null ? stepConfig.getExportFolderName() : "export");
                Files.createDirectories(stepOutputDir);
                List<Long> trackingIds = resolveTrackingIds(selectedBos, dataSource, stepConfig);
                AttachmentDownloader downloader = new AttachmentDownloader(dataSource, stepOutputDir);
                int count = downloader.downloadAttachments(trackingIds);
                LOG.info("EXPORT_ATTACHMENTS: downloaded {} attachment(s)", count);
                updateStep(runId, STEP_EXPORT_ATTACHMENTS, RunStatus.SUCCESS);
            } catch (Exception e) {
                LOG.error("EXPORT_ATTACHMENTS failed: {}", e.getMessage(), e);
                updateStep(runId, STEP_EXPORT_ATTACHMENTS, RunStatus.FAILED);
                failRemaining(runId, STEP_EXPORT_ATTACHMENTS);
                return;
            }

            // PACKAGING
            AppConfig packagingConfig = ConfigLoader.loadRaw(configPath);
            if (!packagingConfig.isEnableZipPackaging()) {
                updateStep(runId, STEP_PACKAGING, RunStatus.SKIPPED);
            } else {
                updateStep(runId, STEP_PACKAGING, RunStatus.IN_PROGRESS);
                try {
                    Files.createDirectories(outputDir);
                    List<Path> parts = new ZipPackager().pack(outputDir, runId);
                    LOG.info("PACKAGING: created {} ZIP part(s)", parts.size());
                    updateStep(runId, STEP_PACKAGING, RunStatus.SUCCESS);
                } catch (Exception e) {
                    LOG.error("PACKAGING failed: {}", e.getMessage(), e);
                    updateStep(runId, STEP_PACKAGING, RunStatus.FAILED);
                    failRemaining(runId, STEP_PACKAGING);
                    return;
                }
            }

            // SFTP_UPLOAD
            AppConfig sftpConfig = ConfigLoader.loadRaw(configPath);
            if (!sftpConfig.isEnableSftpUpload()) {
                updateStep(runId, STEP_SFTP_UPLOAD, RunStatus.SKIPPED);
            } else {
                updateStep(runId, STEP_SFTP_UPLOAD, RunStatus.IN_PROGRESS);
                try {
                    List<Path> filesToUpload = new java.util.ArrayList<>();
                    if (sftpConfig.isEnableZipPackaging()) {
                        // Collect ZIP parts produced by the PACKAGING step
                        Path parentDir = outputDir.getParent() != null ? outputDir.getParent() : outputDir;
                        try (java.nio.file.DirectoryStream<Path> ds =
                                 java.nio.file.Files.newDirectoryStream(parentDir, "*.zip.*")) {
                            for (Path p : ds) {
                                if (java.nio.file.Files.isRegularFile(p)) filesToUpload.add(p);
                            }
                        } catch (Exception ex) {
                            LOG.warn("Could not list ZIP parts: {}", ex.getMessage());
                        }
                    } else {
                        // ZIP packaging is disabled — upload all regular files from outputDir directly
                        try {
                            Files.list(outputDir)
                                    .filter(Files::isRegularFile)
                                    .collect(Collectors.toCollection(() -> filesToUpload));
                        } catch (Exception ex) {
                            LOG.warn("Could not list output files for SFTP upload: {}", ex.getMessage());
                        }
                    }
                    String targetDir = sftpTargetPath != null && !sftpTargetPath.isBlank()
                            ? sftpTargetPath : "/exports";
                    new SftpUploader(sftpConfig.getSftp()).upload(filesToUpload, targetDir);
                    updateStep(runId, STEP_SFTP_UPLOAD, RunStatus.SUCCESS);
                } catch (Exception e) {
                    LOG.error("SFTP_UPLOAD failed: {}", e.getMessage(), e);
                    updateStep(runId, STEP_SFTP_UPLOAD, RunStatus.FAILED);
                }
            }

        } finally {
            if (!offlineDs && dataSource != null) {
                try {
                    dataSource.logout();
                } catch (Exception ex) {
                    LOG.warn("Logout failed (non-fatal): {}", ex.getMessage());
                }
            }
            UiState state = stateStore.read();
            if (state.getCurrentRun() != null && runId.equals(state.getCurrentRun().getRunId())) {
                state.getCurrentRun().setCompletedAt(Instant.now().toString());
                // If all steps succeeded, record last-run time per BO
                Map<String, String> steps = state.getCurrentRun().getSteps();
                boolean allSuccess = steps.values().stream().allMatch(v ->
                    RunStatus.SUCCESS.name().equals(v) || RunStatus.SKIPPED.name().equals(v));
                if (allSuccess) {
                    String completedTime = state.getCurrentRun().getCompletedAt();
                    for (String bo : state.getCurrentRun().getSelectedBos()) {
                        state.getBoLastRun().put(bo, completedTime);
                    }
                }
                // Prepend to history (newest first), cap at 50
                List<UiState.RunState> history = new ArrayList<>(state.getRunHistory());
                history.add(0, state.getCurrentRun());
                if (history.size() > 50) history = history.subList(0, 50);
                state.setRunHistory(new ArrayList<>(history));
                stateStore.write(state);
            }
            running.set(false);
        }
    }

    private List<Long> resolveTrackingIds(List<String> selectedBos, DataSource dataSource,
                                          AppConfig config) {
        List<Long> all = new ArrayList<>();
        List<String> boNames = selectedBos.isEmpty()
                ? config.getBoTypes().stream()
                        .map(BoTypeConfig::getName)
                        .filter(n -> n != null && !n.isBlank())
                        .collect(Collectors.toList())
                : selectedBos;
        for (String bo : boNames) {
            try {
                all.addAll(dataSource.getTrackingNumbers(bo));
            } catch (Exception e) {
                LOG.warn("Could not get tracking numbers for BO {}: {}", bo, e.getMessage());
            }
        }
        return all;
    }

    private void updateStep(String runId, String step, RunStatus status) {
        UiState state = stateStore.read();
        if (state.getCurrentRun() != null && runId.equals(state.getCurrentRun().getRunId())) {
            state.getCurrentRun().getSteps().put(step, status.name());
            stateStore.write(state);
        }
    }

    private void failRemaining(String runId, String failedStep) {
        UiState state = stateStore.read();
        if (state.getCurrentRun() == null) return;
        boolean past = false;
        for (String s : STEPS) {
            if (s.equals(failedStep)) { past = true; continue; }
            if (past) state.getCurrentRun().getSteps().put(s, RunStatus.FAILED.name());
        }
        stateStore.write(state);
    }

    public boolean isRunning() { return running.get(); }

    public boolean stopRun() {
        if (!running.get()) return false;
        Future<?> f = currentFuture;
        if (f != null) f.cancel(true);
        return true;
    }
}
