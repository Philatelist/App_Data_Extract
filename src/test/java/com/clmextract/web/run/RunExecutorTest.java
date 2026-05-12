package com.clmextract.web.run;

import com.clmextract.web.state.StateStore;
import com.clmextract.web.state.UiState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RunExecutorTest {

    private static final String MINIMAL_CONFIG_YAML_TEMPLATE =
        "server:\n" +
        "  url: https://test.example.com\n" +
        "  username: user\n" +
        "  password: pass\n" +
        "endpointsFile: inputs/endpoints.yml\n" +
        "batchSize: 10\n" +
        "outputRoot: %s\n" +
        "delimiter: \",\"\n" +
        "csvMode: per-component\n" +
        "enableZipPackaging: false\n" +
        "enableSftpUpload: false\n";

    @TempDir
    Path tempDir;

    private StateStore stateStore;
    private String configPath;

    @BeforeEach
    void setUp() throws IOException {
        Path configFile = tempDir.resolve("config.yml");
        String configYaml = String.format(MINIMAL_CONFIG_YAML_TEMPLATE, tempDir.toAbsolutePath().toString());
        Files.writeString(configFile, configYaml);
        configPath = configFile.toAbsolutePath().toString();

        Path stateFile = tempDir.resolve("ui-state.json");
        stateStore = new StateStore(stateFile, new ObjectMapper());
    }

    @Test
    void stepTransitions_allStepsSucceed() throws InterruptedException {
        RunExecutor executor = new RunExecutor(configPath, stateStore);

        boolean started = executor.startRun(List.of(), "/test", null, null);
        assertTrue(started, "startRun should return true when no run is in progress");

        // Poll until the run completes (up to 15 seconds)
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            UiState.RunState run = stateStore.read().getCurrentRun();
            if (run != null && run.getCompletedAt() != null) break;
            Thread.sleep(200);
        }

        UiState.RunState run = stateStore.read().getCurrentRun();
        assertNotNull(run, "currentRun must not be null after completion");
        assertNotNull(run.getCompletedAt(), "completedAt must be set after run finishes");

        Map<String, String> steps = run.getSteps();
        assertEquals("SUCCESS", steps.get(RunExecutor.STEP_EXPORT_CSV),       "EXPORT_CSV should be SUCCESS");
        assertEquals("SUCCESS", steps.get(RunExecutor.STEP_EXPORT_PDF),        "EXPORT_PDF should be SUCCESS");
        assertEquals("SUCCESS", steps.get(RunExecutor.STEP_EXPORT_ATTACHMENTS),"EXPORT_ATTACHMENTS should be SUCCESS");
        assertEquals("SKIPPED", steps.get(RunExecutor.STEP_PACKAGING),         "PACKAGING should be SKIPPED (disabled in test config)");
        assertEquals("SKIPPED", steps.get(RunExecutor.STEP_SFTP_UPLOAD),       "SFTP_UPLOAD should be SKIPPED (disabled in test config)");
    }

    @Test
    void concurrentStart_secondStartReturnsFalse() throws InterruptedException {
        RunExecutor executor = new RunExecutor(configPath, stateStore);

        boolean first = executor.startRun(List.of(), "/test", null, null);
        assertTrue(first, "First startRun should return true");

        boolean second = executor.startRun(List.of(), "/test", null, null);
        assertFalse(second, "Second startRun while first is running should return false (caller should 409)");

        // Wait for the first run to complete before the test tears down
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            UiState.RunState run = stateStore.read().getCurrentRun();
            if (run != null && run.getCompletedAt() != null) break;
            Thread.sleep(200);
        }

        UiState.RunState run = stateStore.read().getCurrentRun();
        assertNotNull(run, "currentRun must not be null after completion");
        assertNotNull(run.getCompletedAt(), "First run must complete within 15 seconds");
    }

    @Test
    void packagingSkipped_whenEnableZipPackagingFalse() throws InterruptedException {
        // The default MINIMAL_CONFIG_YAML_TEMPLATE already has enableZipPackaging: false
        RunExecutor executor = new RunExecutor(configPath, stateStore);
        executor.startRun(List.of(), "/test", null, null);

        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            UiState.RunState run = stateStore.read().getCurrentRun();
            if (run != null && run.getCompletedAt() != null) break;
            Thread.sleep(200);
        }

        Map<String, String> steps = stateStore.read().getCurrentRun().getSteps();
        assertEquals("SKIPPED", steps.get(RunExecutor.STEP_PACKAGING), "PACKAGING should be SKIPPED when disabled");
    }

    @Test
    void sftpUploadSkipped_whenEnableSftpUploadFalse() throws InterruptedException {
        // The default MINIMAL_CONFIG_YAML_TEMPLATE already has enableSftpUpload: false
        RunExecutor executor = new RunExecutor(configPath, stateStore);
        executor.startRun(List.of(), "/test", null, null);

        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            UiState.RunState run = stateStore.read().getCurrentRun();
            if (run != null && run.getCompletedAt() != null) break;
            Thread.sleep(200);
        }

        Map<String, String> steps = stateStore.read().getCurrentRun().getSteps();
        assertEquals("SKIPPED", steps.get(RunExecutor.STEP_SFTP_UPLOAD), "SFTP_UPLOAD should be SKIPPED when disabled");
    }
}
