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

    public String resolve(String template, String boName, String componentName) {
        return template
                .replace("{BO}", boName != null ? boName : "")
                .replace("{Component}", componentName != null ? componentName : "")
                .replace("{DDMMYYYY}", dateStr)
                .replace("{HHMMSS}", timeStr);
    }
}
