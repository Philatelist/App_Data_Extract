package com.clmextract.csv;

import com.clmextract.config.DateFormatConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class DateFormatter {

    private static final Logger logger = LogManager.getLogger(DateFormatter.class);

    private final DateFormatConfig config;

    public DateFormatter(DateFormatConfig config) {
        this.config = config;
    }

    public String format(String value, String dataType) {
        if (config == null) {
            return value;
        }

        List<String> inputFormats;
        String outputFormat;
        boolean isDateTime;

        if ("genericDate".equals(dataType) || "modernDate".equals(dataType)) {
            inputFormats = config.getInputFormats();
            outputFormat = config.getOutputFormat();
            isDateTime = false;
        } else if ("modernDateTime".equals(dataType)) {
            inputFormats = config.getInputDateTimeFormats();
            outputFormat = config.getOutputDateTimeFormat();
            isDateTime = true;
        } else {
            return value;
        }

        if (inputFormats == null || inputFormats.isEmpty() || outputFormat == null || outputFormat.isEmpty()) {
            return value;
        }

        if (value == null || value.isBlank()) {
            return value;
        }

        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(outputFormat);
        for (String inputFormat : inputFormats) {
            try {
                DateTimeFormatter parser = DateTimeFormatter.ofPattern(inputFormat);
                if (isDateTime) {
                    return LocalDateTime.parse(value, parser).format(outputFormatter);
                } else {
                    return LocalDate.parse(value, parser).format(outputFormatter);
                }
            } catch (DateTimeParseException e) {
                // try next format
            }
        }

        logger.debug("Could not parse date value '{}' using any configured input format, passing through as-is", value);
        return value;
    }
}
