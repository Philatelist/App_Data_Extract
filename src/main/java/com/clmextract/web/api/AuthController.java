package com.clmextract.web.api;

import com.clmextract.config.AppConfig;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.http.RequestExecutor;
import com.clmextract.session.SessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class AuthController {

    private static final Logger logger = LogManager.getLogger(AuthController.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    private final AppConfig config;
    private final EndpointRegistry endpointRegistry;

    public AuthController(AppConfig config, EndpointRegistry endpointRegistry) {
        this.config = config;
        this.endpointRegistry = endpointRegistry;
    }

    public void checkAdmin(Context ctx) throws Exception {
        String email = ctx.queryParam("email");
        if (email == null || email.isBlank() || config == null || config.getAdminEmails() == null) {
            ctx.contentType("application/json").result(mapper.writeValueAsString(Map.of("isAdmin", false)));
            return;
        }
        boolean isAdmin = config.getAdminEmails().stream()
                .anyMatch(e -> matchesAdminEntry(e, email.trim()));
        ctx.contentType("application/json").result(mapper.writeValueAsString(Map.of("isAdmin", isAdmin)));
    }

    public void login(Context ctx) throws Exception {
        Map<?, ?> body = ctx.bodyAsClass(Map.class);
        String username = body != null ? (String) body.get("username") : null;
        String password = body != null ? (String) body.get("password") : null;
        boolean asAdmin = body != null && Boolean.TRUE.equals(body.get("asAdmin"));

        // Attempt CLM login
        if (config == null || endpointRegistry == null) {
            logger.warn("CLM config or endpoint registry not available; rejecting login");
            ctx.status(401).result(mapper.writeValueAsString(Map.of("error", "Invalid credentials")));
            ctx.contentType("application/json");
            return;
        }

        try {
            // Build a temporary AppConfig that uses the submitted credentials
            AppConfig loginConfig = buildLoginConfig(username, password);
            RequestExecutor requestExecutor = new RequestExecutor(loginConfig);
            SessionManager sessionManager = new SessionManager(loginConfig, endpointRegistry, requestExecutor);

            sessionManager.login();
            String sessionId = sessionManager.getSessionId();

            boolean isAdminEmail = asAdmin && config.getAdminEmails() != null
                    && config.getAdminEmails().stream().anyMatch(e -> matchesAdminEntry(e, username));
            String role = isAdminEmail ? "ADMIN" : "OPERATOR";
            ctx.sessionAttribute("role", role);
            ctx.sessionAttribute("clmSessionId", sessionId);
            ctx.status(200).result(mapper.writeValueAsString(Map.of("role", role)));
            ctx.contentType("application/json");
        } catch (Exception e) {
            logger.warn("CLM login failed: {}", e.getMessage());
            ctx.status(401).result(mapper.writeValueAsString(Map.of("error", "Invalid credentials")));
            ctx.contentType("application/json");
        }
    }

    public void logout(Context ctx) throws Exception {
        ctx.req().getSession().invalidate();
        ctx.status(200).result(mapper.writeValueAsString(Map.of("ok", true)));
        ctx.contentType("application/json");
    }

    /**
     * Matches a submitted username/email against an adminEmails entry.
     * Accepts an exact case-insensitive match, or — when the submitted value has no '@' —
     * matches against the local-part of the configured email (the part before '@').
     * This lets CLM short usernames (e.g. "ausov") match a configured entry of "ausov@corp.com".
     */
    static boolean matchesAdminEntry(String configuredEntry, String submitted) {
        if (configuredEntry == null || submitted == null) return false;
        if (configuredEntry.equalsIgnoreCase(submitted)) return true;
        // If the submitted value has no '@', also compare against the local-part of the configured email
        if (!submitted.contains("@") && configuredEntry.contains("@")) {
            String localPart = configuredEntry.substring(0, configuredEntry.indexOf('@'));
            return localPart.equalsIgnoreCase(submitted);
        }
        return false;
    }

    /**
     * Builds a temporary AppConfig that inherits all settings from the global config
     * but overrides username and password with the values supplied at login time.
     * This lets the user authenticate with their own CLM credentials rather than
     * the service-account credentials stored in config.yml.
     */
    private AppConfig buildLoginConfig(String username, String password) {
        AppConfig loginConfig = new AppConfig();
        loginConfig.setBaseUrl(config.getBaseUrl());
        loginConfig.setUsername(username);
        loginConfig.setPassword(password);
        loginConfig.setRetryMaxAttempts(config.getRetryMaxAttempts());
        loginConfig.setRetryBaseDelayMs(config.getRetryBaseDelayMs());
        loginConfig.setEndpointsFile(config.getEndpointsFile());
        return loginConfig;
    }
}
