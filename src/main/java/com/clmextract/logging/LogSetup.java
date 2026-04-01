package com.clmextract.logging;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogSetup {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy_HHmmss");

    /**
     * Sets the {@code clm.logfile} system property so that the log4j2.xml File appender
     * resolves to the correct timestamped path under {@code outputRoot/logs/}.
     * Must be called before the first Log4j2 logger is obtained.
     */
    public static void configure(String outputRoot) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        try {
            Path logsDir = Path.of(outputRoot, "logs");
            Files.createDirectories(logsDir);
            System.setProperty("clm.logfile", logsDir.resolve("run_" + timestamp + ".log").toString());
        } catch (Exception e) {
            System.err.println("Warning: Could not configure file logging: " + e.getMessage());
        }
    }
}
