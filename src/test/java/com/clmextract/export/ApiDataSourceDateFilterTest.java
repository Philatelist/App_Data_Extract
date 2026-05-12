package com.clmextract.export;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import static org.junit.jupiter.api.Assertions.*;

class ApiDataSourceDateFilterTest {

    private String convertToCLMDateFormat(String isoDate) {
        LocalDate date = LocalDate.parse(isoDate);
        return date.atStartOfDay().format(DateTimeFormatter.ofPattern("dd-M-yyyy HH:mm:ss"));
    }

    @Test
    void dateConversion_januaryDate_singleDigitMonth() {
        String result = convertToCLMDateFormat("2026-01-15");
        assertEquals("15-1-2026 00:00:00", result);
    }

    @Test
    void dateConversion_octoberDate_doubleDigitMonth() {
        String result = convertToCLMDateFormat("2026-10-05");
        assertEquals("05-10-2026 00:00:00", result);
    }

    @Test
    void dateConversion_firstDayOfMonth() {
        String result = convertToCLMDateFormat("2026-03-01");
        assertEquals("01-3-2026 00:00:00", result);
    }

    @Test
    void dateConversion_lastDayOfYear() {
        String result = convertToCLMDateFormat("2026-12-31");
        assertEquals("31-12-2026 00:00:00", result);
    }
}
