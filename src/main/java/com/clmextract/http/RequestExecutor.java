package com.clmextract.http;

import com.clmextract.config.AppConfig;
import com.clmextract.endpoint.AuthConfig;
import com.clmextract.endpoint.EndpointDefinition;
import com.clmextract.endpoint.HeaderDef;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class RequestExecutor {

    private static final Logger logger = LogManager.getLogger(RequestExecutor.class);

    private final HttpClient httpClient;
    private final AppConfig config;
    private final RetryPolicy retryPolicy;
    private SessionReLoginHandler reLoginHandler;

    public RequestExecutor(AppConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newHttpClient();
        this.retryPolicy = new RetryPolicy(config.getRetryMaxAttempts(), config.getRetryBaseDelayMs());
    }

    public void setReLoginHandler(SessionReLoginHandler reLoginHandler) {
        this.reLoginHandler = reLoginHandler;
    }

    public String execute(EndpointDefinition endpoint, Map<String, String> dynamicHeaders, String body) {
        try {
            return retryPolicy.execute(() -> doExecute(endpoint, dynamicHeaders, body));
        } catch (SessionExpiredException e) {
            if (reLoginHandler == null) {
                throw e;
            }
            logger.info("Session expired, attempting re-login...");
            reLoginHandler.onSessionExpired();
            // Update session_id in dynamic headers after re-login
            if (dynamicHeaders != null && reLoginHandler.getNewSessionId() != null) {
                dynamicHeaders.put("session_id", reLoginHandler.getNewSessionId());
            }
            try {
                return retryPolicy.execute(() -> doExecute(endpoint, dynamicHeaders, body));
            } catch (SessionExpiredException e2) {
                logger.error("Session expired again after re-login, giving up");
                throw e2;
            }
        }
    }

    private String doExecute(EndpointDefinition endpoint, Map<String, String> dynamicHeaders, String body) {
        String url = config.getBaseUrl() + endpoint.getPath();
        logger.debug("Executing {} {} ", endpoint.getMethod(), url);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        // Set HTTP method and body
        HttpRequest.BodyPublisher bodyPublisher = (body != null)
                ? HttpRequest.BodyPublishers.ofString(body)
                : HttpRequest.BodyPublishers.noBody();

        switch (endpoint.getMethod().toUpperCase()) {
            case "GET":
                builder.GET();
                break;
            case "POST":
                builder.POST(bodyPublisher);
                break;
            case "PUT":
                builder.PUT(bodyPublisher);
                break;
            case "DELETE":
                builder.DELETE();
                break;
            default:
                builder.method(endpoint.getMethod(), bodyPublisher);
        }

        // Set content type for requests with body
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }

        // Attach auth headers
        AuthConfig auth = endpoint.getAuth();
        if (auth != null) {
            if ("basicHeaders".equals(auth.getType())) {
                builder.header("user", config.getUsername());
                builder.header("password", config.getPassword());
            } else if ("sessionHeader".equals(auth.getType())) {
                String headerName = auth.getHeaderName() != null ? auth.getHeaderName() : "session_id";
                String sessionId = dynamicHeaders != null ? dynamicHeaders.get("session_id") : null;
                if (sessionId != null) {
                    builder.header(headerName, sessionId);
                }
            }
        }

        // Attach static headers from endpoint definition
        if (endpoint.getRequestHeaders() != null) {
            for (HeaderDef hdr : endpoint.getRequestHeaders()) {
                String value = null;
                if (hdr.getValue() != null) {
                    value = hdr.getValue();
                } else if (hdr.getValueFrom() != null && dynamicHeaders != null) {
                    value = dynamicHeaders.get(hdr.getValueFrom());
                }
                if (value != null) {
                    builder.header(hdr.getName(), value);
                }
            }
        }

        HttpRequest request = builder.build();
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException("IO error during request: " + endpoint.getName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted: " + endpoint.getName(), e);
        }

        int status = response.statusCode();
        String responseBody = response.body();

        if (status >= 200 && status < 300) {
            return responseBody;
        }

        if (status == 401 || status == 403) {
            throw new SessionExpiredException(status, responseBody);
        }

        throw new ApiException(status, responseBody);
    }
}
