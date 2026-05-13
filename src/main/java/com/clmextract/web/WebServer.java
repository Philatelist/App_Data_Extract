package com.clmextract.web;

import com.clmextract.config.AppConfig;
import com.clmextract.config.ConfigLoader;
import com.clmextract.config.ConfigValidationException;
import com.clmextract.config.CredentialEncryptor;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.web.api.AdminController;
import com.clmextract.web.api.AuthController;
import com.clmextract.web.api.ConfigController;
import com.clmextract.web.api.RunController;
import com.clmextract.web.run.RunExecutor;
import com.clmextract.web.scheduler.ExportScheduler;
import com.clmextract.web.state.StateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.http.staticfiles.Location;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.Optional;

public class WebServer {

    private static final Logger logger = LogManager.getLogger(WebServer.class);

    /**
     * Fails fast if any password field in {@code config} is stored as an ENC(...) token
     * but the {@code CLM_EXTRACT_KEY} environment variable is not set.
     * Package-private for unit testing.
     */
    static void checkEncryptedCredentials(AppConfig config) {
        Optional<String> masterKey = CredentialEncryptor.getMasterKey();
        if (masterKey.isEmpty()) {
            boolean hasEncrypted =
                CredentialEncryptor.isEncrypted(config.getPassword()) ||
                (config.getSftp() != null && CredentialEncryptor.isEncrypted(config.getSftp().getPassword()));
            if (hasEncrypted) {
                throw new IllegalStateException(
                    "Encrypted credentials found but CLM_EXTRACT_KEY is not set. " +
                    "Set the CLM_EXTRACT_KEY environment variable and restart.");
            }
        }
    }

    public static void start(String configPath, int port) {

        // --- Fail fast if encrypted passwords are present but CLM_EXTRACT_KEY is not set ---
        if (configPath != null) {
            try {
                AppConfig rawConfig = ConfigLoader.loadRaw(configPath);
                checkEncryptedCredentials(rawConfig);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                // If raw config can't be loaded, let the regular load path handle the error
                logger.warn("Could not perform encrypted-credentials pre-check: {}", e.getMessage());
            }
        }

        // --- Load config (best-effort; validation failures are non-fatal in serve mode) ---
        AppConfig config = null;
        if (configPath != null) {
            try {
                config = ConfigLoader.load(configPath);
            } catch (ConfigValidationException e) {
                logger.warn("Config has validation warnings (some fields may be blank), continuing in serve mode: {}",
                        e.getMessage());
                // config remains null; admin login still works without CLM config
            } catch (Exception e) {
                logger.warn("Failed to load config from '{}', continuing in serve mode: {}",
                        configPath, e.getMessage());
            }
        }

        // --- Load endpoint registry (best-effort) ---
        EndpointRegistry endpointRegistry = null;
        if (config != null) {
            try {
                endpointRegistry = new EndpointRegistry(config.getEndpointsFile());
                endpointRegistry.load();
            } catch (Exception e) {
                logger.warn("Failed to load endpoint registry, CLM login will not be available: {}",
                        e.getMessage());
                endpointRegistry = null;
            }
        }

        final AppConfig finalConfig = config;
        final EndpointRegistry finalRegistry = endpointRegistry;

        // --- Build shared ObjectMapper and state infrastructure ---
        ObjectMapper objectMapper = new ObjectMapper();
        Path configAbsPath = Path.of(configPath != null ? configPath : "config.yml").toAbsolutePath();
        Path stateFilePath = configAbsPath.getParent().resolve("ui-state.json");
        StateStore stateStore = new StateStore(stateFilePath, objectMapper);
        RunExecutor runExecutor = new RunExecutor(configPath, stateStore);
        ExportScheduler exportScheduler = new ExportScheduler(stateStore, runExecutor);

        // --- Build controllers ---
        AuthController authController = new AuthController(finalConfig, finalRegistry);
        ConfigController configController = new ConfigController(configPath, objectMapper);
        RunController runController = new RunController(configPath, stateStore, runExecutor, objectMapper, exportScheduler);
        AdminController adminController = new AdminController(configPath, finalConfig, stateStore);

        // --- Build Javalin app ---
        Javalin app = Javalin.create(cfg -> {
            cfg.staticFiles.add("/static", Location.CLASSPATH);
        });

        // Auth filter — must be registered before routes
        app.before(ctx -> {
            String path = ctx.path();
            String role = ctx.sessionAttribute("role");

            // Protect admin.html: redirect to login if not ADMIN
            if (path.equals("/admin.html") || path.startsWith("/admin.html?")) {
                if (!"ADMIN".equals(role)) {
                    ctx.redirect("/index.html");
                    return;
                }
            }

            // Protect dashboard.html: redirect to login if not OPERATOR
            if (path.equals("/dashboard.html") || path.startsWith("/dashboard.html?")) {
                if (!"OPERATOR".equals(role)) {
                    ctx.redirect("/index.html");
                    return;
                }
            }

            // Allow login endpoint and non-API paths (static assets, HTML pages) through
            if (path.equals("/api/auth/login") || path.equals("/api/auth/check-admin") || !path.startsWith("/api/")) {
                return;
            }

            // Protect all other /api/* routes
            if (role == null) {
                throw new UnauthorizedResponse("Unauthorized");
            }
        });

        // Auth routes
        app.post("/api/auth/login", authController::login);
        app.post("/api/auth/logout", authController::logout);
        app.get("/api/auth/check-admin", authController::checkAdmin);

        // Config routes
        app.get("/api/config", configController::getConfig);
        app.put("/api/config", configController::putConfig);

        // Run routes
        app.get("/api/bos", runController::getBos);
        app.post("/api/run/start", runController::startRun);
        app.get("/api/run/status", runController::getRunStatus);
        app.get("/api/run/history", runController::getRunHistory);
        app.post("/api/run/stop", runController::stopRun);
        app.get("/api/schedule", runController::getSchedule);
        app.put("/api/schedule", runController::putSchedule);
        app.delete("/api/schedule", runController::deleteSchedule);

        // Admin routes
        app.get("/api/admin/bo-types", adminController::getBoTypes);
        app.get("/api/admin/cached-bo-types", adminController::getCachedBoTypes);
        app.get("/api/admin/bo-metadata/{boType}", adminController::getBoMetadata);
        app.get("/api/admin/columns/{boType}", adminController::getColumns);
        app.put("/api/admin/columns/{boType}", adminController::putColumns);

        // Root redirect (keep existing behaviour)
        app.get("/", ctx -> ctx.redirect("/index.html"));

        app.start(port);
        runExecutor.recoverStaleRun();
        exportScheduler.start();

        System.out.println("Web server started on port " + port);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
