package com.clmextract.csv;

import com.clmextract.config.DateFormatConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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

        String inputFormat;
        String outputFormat;
        boolean isDateTime;

        if ("genericDate".equals(dataType)) {
            inputFormat = config.getInputFormat();
            outputFormat = config.getOutputFormat();
            isDateTime = false;
        } else if ("modernDate".equals(dataType)) {
            inputFormat = config.getInputDateTimeFormat();
            outputFormat = config.getOutputDateTimeFormat();
            isDateTime = true;
        } else {
            return value;
        }

        if (inputFormat == null || inputFormat.isEmpty() || outputFormat == null || outputFormat.isEmpty()) {
            return value;
        }

        if (value == null || value.isBlank()) {
            return value;
        }

        try {
            if (isDateTime) {
                DateTimeFormatter parser = DateTimeFormatter.ofPattern(inputFormat);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(outputFormat);
                LocalDateTime dt = LocalDateTime.parse(value, parser);
                return dt.format(formatter);
            } else {
                DateTimeFormatter parser = DateTimeFormatter.ofPattern(inputFormat);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(outputFormat);
                LocalDate date = LocalDate.parse(value, parser);
                return date.format(formatter);
            }
        } catch (DateTimeParseException e) {
            logger.debug("Could not parse date value '{}' using input format '{}', passing through as-is", value, inputFormat);
            return value;
        }
    }
}
