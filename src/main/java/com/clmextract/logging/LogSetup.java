package com.clmextract.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogSetup {

    private static final String PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy_HHmmss");

    public static void configure(String outputRoot) {
        ConfigurationBuilder<BuiltConfiguration> builder =
                ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(Level.WARN);

        RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.INFO);

        // Console appender — always added
        AppenderComponentBuilder console = builder.newAppender("Console", "CONSOLE")
                .addAttribute("target", "SYSTEM_OUT")
                .add(builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN));
        builder.add(console);
        rootLogger.add(builder.newAppenderRef("Console"));

        // File appender — added only if the logs directory can be created
        try {
            Path logsDir = Path.of(outputRoot, "logs");
            Files.createDirectories(logsDir);
            String logFileName = logsDir.resolve(
                    "run_" + LocalDateTime.now().format(TIMESTAMP_FORMAT) + ".log").toString();

            AppenderComponentBuilder file = builder.newAppender("File", "FILE")
                    .addAttribute("fileName", logFileName)
                    .addAttribute("immediateFlush", true)
                    .add(builder.newLayout("PatternLayout").addAttribute("pattern", PATTERN));
            builder.add(file);
            rootLogger.add(builder.newAppenderRef("File"));
        } catch (Exception e) {
            System.err.println("Warning: Could not configure file logging: " + e.getMessage());
        }

        builder.add(rootLogger);
        Configurator.reconfigure(builder.build());
    }
}
