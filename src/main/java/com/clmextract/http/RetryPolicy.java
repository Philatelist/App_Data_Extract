package com.clmextract.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.function.Supplier;

public class RetryPolicy {

    private static final Logger logger = LogManager.getLogger(RetryPolicy.class);

    private final int maxAttempts;
    private final long baseDelayMs;

    public RetryPolicy(int maxAttempts, long baseDelayMs) {
        this.maxAttempts = maxAttempts;
        this.baseDelayMs = baseDelayMs;
    }

    public String execute(Supplier<String> action) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return action.get();
            } catch (ApiException e) {
                if (e instanceof SessionExpiredException) {
                    throw e;
                }
                // Don't retry client errors (4xx) or deterministic server errors (500)
                if (e.getStatusCode() >= 400 && e.getStatusCode() < 500 || e.getStatusCode() == 500) {
                    throw e;
                }
                if (attempt >= maxAttempts) {
                    throw e;
                }
                long delay = baseDelayMs * (long) Math.pow(2, attempt - 1);
                logger.warn("Request failed (attempt {}/{}), retrying in {}ms: {}",
                        attempt, maxAttempts, delay, e.getMessage());
                sleep(delay);
            } catch (RuntimeException e) {
                if (e.getCause() instanceof IOException) {
                    if (attempt >= maxAttempts) {
                        throw new ApiException("Request failed after " + maxAttempts + " attempts", e);
                    }
                    long delay = baseDelayMs * (long) Math.pow(2, attempt - 1);
                    logger.warn("IO error (attempt {}/{}), retrying in {}ms: {}",
                            attempt, maxAttempts, delay, e.getMessage());
                    sleep(delay);
                } else {
                    throw e;
                }
            }
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry interrupted", e);
        }
    }
}
