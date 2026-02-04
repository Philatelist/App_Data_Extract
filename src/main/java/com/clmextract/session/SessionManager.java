package com.clmextract.session;

import com.clmextract.config.AppConfig;
import com.clmextract.endpoint.EndpointDefinition;
import com.clmextract.endpoint.EndpointRegistry;
import com.clmextract.http.RequestExecutor;
import com.clmextract.http.SessionReLoginHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class SessionManager implements SessionReLoginHandler {

    private static final Logger logger = LogManager.getLogger(SessionManager.class);

    private final AppConfig config;
    private final EndpointRegistry endpointRegistry;
    private final RequestExecutor requestExecutor;
    private String sessionId;

    public SessionManager(AppConfig config, EndpointRegistry endpointRegistry,
                          RequestExecutor requestExecutor) {
        this.config = config;
        this.endpointRegistry = endpointRegistry;
        this.requestExecutor = requestExecutor;
    }

    public void ensureLoggedIn() {
        if (sessionId == null) {
            login();
        }
    }

    public void login() {
        logger.info("Logging in as user: {}", config.getUsername());
        EndpointDefinition loginEndpoint = endpointRegistry.getEndpoint(EndpointRegistry.LOGIN);

        Map<String, String> headers = new HashMap<>();
        // basicHeaders auth type sends user/password — handled by RequestExecutor

        String response = requestExecutor.execute(loginEndpoint, headers, null);
        sessionId = response.trim();
        logger.info("Login successful, session established");
    }

    public void logout() {
        if (sessionId == null) {
            logger.debug("No active session, skipping logout");
            return;
        }
        try {
            logger.info("Logging out...");
            EndpointDefinition logoutEndpoint = endpointRegistry.getEndpoint(EndpointRegistry.LOGOUT);

            Map<String, String> headers = new HashMap<>();
            headers.put("session_id", sessionId);

            requestExecutor.execute(logoutEndpoint, headers, null);
            sessionId = null;
            logger.info("Logout successful");
        } catch (Exception e) {
            logger.warn("Logout failed (non-fatal): {}", e.getMessage());
            sessionId = null;
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public void invalidateSession() {
        logger.info("Session invalidated, will re-login on next request");
        sessionId = null;
    }

    @Override
    public void onSessionExpired() {
        invalidateSession();
        login();
    }

    @Override
    public String getNewSessionId() {
        return sessionId;
    }
}
