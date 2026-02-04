package com.clmextract.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogSetup {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("ddMMyyyy_HHmmss");

    public static void configure(String outputRoot) {
        try {
            Path logsDir = Path.of(outputRoot, "logs");
            Files.createDirectories(logsDir);

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String logFileName = logsDir.resolve("run_" + timestamp + ".log").toString();

            LoggerContext context = LoggerContext.getContext(false);
            Configuration config = context.getConfiguration();

            PatternLayout layout = PatternLayout.newBuilder()
                    .withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n")
                    .withConfiguration(config)
                    .build();

            // File appender
            FileAppender fileAppender = FileAppender.newBuilder()
                    .setName("FileAppender")
                    .withFileName(logFileName)
                    .setLayout(layout)
                    .setConfiguration(config)
                    .build();
            fileAppender.start();

            // Console appender
            ConsoleAppender consoleAppender = ConsoleAppender.newBuilder()
                    .setName("ConsoleAppender")
                    .setLayout(layout)
                    .setConfiguration(config)
                    .build();
            consoleAppender.start();

            config.addAppender(fileAppender);
            config.addAppender(consoleAppender);

            LoggerConfig rootLogger = config.getRootLogger();
            rootLogger.addAppender(fileAppender, Level.INFO, null);
            rootLogger.addAppender(consoleAppender, Level.INFO, null);
            rootLogger.setLevel(Level.INFO);

            context.updateLoggers();
        } catch (Exception e) {
            System.err.println("Warning: Could not configure logging: " + e.getMessage());
        }
    }
}
