package com.clmextract.csv;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FilenameResolver {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private final String dateStr;
    private final String timeStr;

    public FilenameResolver() {
        LocalDateTime now = LocalDateTime.now();
        this.dateStr = now.format(DATE_FORMAT);
        this.timeStr = now.format(TIME_FORMAT);
    }

    FilenameResolver(String dateStr, String timeStr) {
        this.dateStr = dateStr;
        this.timeStr = timeStr;
    }

    static String sanitize(String token) {
        if (token == null || token.isEmpty()) return "";
        String s = token.replace(' ', '_');
        s = s.replaceAll("[^A-Za-z0-9._-]", "_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^[_.]+|[_.]+$", "");
        return s;
    }

    public String resolve(String template, String boName, String componentName) {
        return template
                .replace("{BO}", sanitize(boName))
                .replace("{Component}", sanitize(componentName))
                .replace("{DDMMYYYY}", dateStr)
                .replace("{HHMMSS}", timeStr);
    }
}
