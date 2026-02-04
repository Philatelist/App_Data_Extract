package com.clmextract.logging;

import com.clmextract.endpoint.EndpointDefinition;
import com.clmextract.http.RequestExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.time.Duration;
import java.util.Map;

public class RestCallLogger {

    private static final Logger logger = LogManager.getLogger(RestCallLogger.class);

    private final RequestExecutor requestExecutor;

    public RestCallLogger(RequestExecutor requestExecutor) {
        this.requestExecutor = requestExecutor;
    }

    public String execute(EndpointDefinition endpoint, Map<String, String> dynamicHeaders,
                          String body, String boName) {
        Instant start = Instant.now();
        String endpointName = endpoint.getName();
        String bo = boName != null ? boName : "N/A";

        try {
            String result = requestExecutor.execute(endpoint, dynamicHeaders, body);
            Instant end = Instant.now();
            long elapsed = Duration.between(start, end).toMillis();
            logger.info("[{}] {} | BO: {} | {}ms | SUCCESS",
                    start, endpointName, bo, elapsed);
            return result;
        } catch (Exception e) {
            Instant end = Instant.now();
            long elapsed = Duration.between(start, end).toMillis();
            logger.error("[{}] {} | BO: {} | {}ms | FAILURE | {}",
                    start, endpointName, bo, elapsed, e.getMessage());
            throw e;
        }
    }
}
