package com.clmextract.csv;

import com.clmextract.config.DateFormatConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DateFormatterTest {

    private static DateFormatConfig fullConfig() {
        DateFormatConfig c = new DateFormatConfig();
        c.setInputFormats(List.of("M/d/yyyy"));
        c.setOutputFormat("dd/MM/yyyy");
        c.setInputDateTimeFormats(List.of("M/d/yyyy h:mm:ss a", "M/d/yyyy HH:mm:ss", "M/d/yyyy HH:mm"));
        c.setOutputDateTimeFormat("dd/MM/yyyy HH:mm:ss");
        return c;
    }

    private static DateFormatConfig datePairOnly() {
        DateFormatConfig c = new DateFormatConfig();
        c.setInputFormats(List.of("M/d/yyyy"));
        c.setOutputFormat("dd/MM/yyyy");
        return c;
    }

    private static DateFormatConfig dateTimePairOnly() {
        DateFormatConfig c = new DateFormatConfig();
        c.setInputDateTimeFormats(List.of("M/d/yyyy h:mm:ss a", "M/d/yyyy HH:mm:ss", "M/d/yyyy HH:mm"));
        c.setOutputDateTimeFormat("dd/MM/yyyy HH:mm:ss");
        return c;
    }

    // --- null config (feature disabled) ---

    @Test
    void nullConfig_genericDate_passThrough() {
        assertEquals("1/15/2024", new DateFormatter(null).format("1/15/2024", "genericDate"));
    }

    @Test
    void nullConfig_modernDate_passThrough() {
        assertEquals("1/15/2024", new DateFormatter(null).format("1/15/2024", "modernDate"));
    }

    @Test
    void nullConfig_modernDateTime_passThrough() {
        assertEquals("1/15/2024 09:30:00", new DateFormatter(null).format("1/15/2024 09:30:00", "modernDateTime"));
    }

    @Test
    void nullConfig_unknownType_passThrough() {
        assertEquals("someValue", new DateFormatter(null).format("someValue", "text"));
    }

    // --- genericDate ---

    @Test
    void genericDate_reformatted() {
        assertEquals("15/01/2024", new DateFormatter(fullConfig()).format("1/15/2024", "genericDate"));
    }

    @Test
    void genericDate_singleDigitDayAndMonth_reformatted() {
        assertEquals("05/03/2024", new DateFormatter(fullConfig()).format("3/5/2024", "genericDate"));
    }

    @Test
    void genericDate_blankValue_passThrough() {
        assertEquals("", new DateFormatter(fullConfig()).format("", "genericDate"));
    }

    @Test
    void genericDate_nullValue_passThrough() {
        assertNull(new DateFormatter(fullConfig()).format(null, "genericDate"));
    }

    @Test
    void genericDate_unparseable_passThrough() {
        assertEquals("not-a-date", new DateFormatter(fullConfig()).format("not-a-date", "genericDate"));
    }

    @Test
    void genericDate_datePairMissing_passThrough() {
        assertEquals("1/15/2024", new DateFormatter(dateTimePairOnly()).format("1/15/2024", "genericDate"));
    }

    // --- modernDate ---

    @Test
    void modernDate_reformatted() {
        assertEquals("15/01/2024", new DateFormatter(fullConfig()).format("1/15/2024", "modernDate"));
    }

    @Test
    void modernDate_blankValue_passThrough() {
        assertEquals("   ", new DateFormatter(fullConfig()).format("   ", "modernDate"));
    }

    @Test
    void modernDate_nullValue_passThrough() {
        assertNull(new DateFormatter(fullConfig()).format(null, "modernDate"));
    }

    @Test
    void modernDate_unparseable_passThrough() {
        assertEquals("31/31/2024", new DateFormatter(fullConfig()).format("31/31/2024", "modernDate"));
    }

    @Test
    void modernDate_datePairMissing_passThrough() {
        assertEquals("1/15/2024", new DateFormatter(dateTimePairOnly()).format("1/15/2024", "modernDate"));
    }

    // --- modernDateTime: 12-hour AM/PM with seconds ---

    @Test
    void modernDateTime_12hAmPm_withSeconds_reformatted() {
        assertEquals("30/07/2025 12:59:05", new DateFormatter(fullConfig()).format("7/30/2025 12:59:05 PM", "modernDateTime"));
    }

    @Test
    void modernDateTime_12hAmPm_singleDigitHour_reformatted() {
        assertEquals("30/03/2020 13:28:29", new DateFormatter(fullConfig()).format("3/30/2020 1:28:29 PM", "modernDateTime"));
    }

    @Test
    void modernDateTime_12hAmPm_am_reformatted() {
        assertEquals("29/01/2020 01:35:02", new DateFormatter(fullConfig()).format("1/29/2020 1:35:02 AM", "modernDateTime"));
    }

    // --- modernDateTime: 24-hour without seconds ---

    @Test
    void modernDateTime_24h_noSeconds_reformatted() {
        assertEquals("02/01/2025 14:09:00", new DateFormatter(fullConfig()).format("1/2/2025 14:09", "modernDateTime"));
    }

    @Test
    void modernDateTime_24h_noSeconds_leadingZero_reformatted() {
        assertEquals("11/07/2025 07:33:00", new DateFormatter(fullConfig()).format("7/11/2025 07:33", "modernDateTime"));
    }

    // --- modernDateTime: 24-hour with seconds ---

    @Test
    void modernDateTime_24h_withSeconds_reformatted() {
        assertEquals("15/01/2024 09:30:00", new DateFormatter(fullConfig()).format("1/15/2024 09:30:00", "modernDateTime"));
    }

    // --- modernDateTime: fallback order — first matching format wins ---

    @Test
    void modernDateTime_firstFormatMatchesFirst_usedDirectly() {
        // "M/d/yyyy h:mm:ss a" is first; "3/14/2025 4:53:18 PM" matches it
        assertEquals("14/03/2025 16:53:18", new DateFormatter(fullConfig()).format("3/14/2025 4:53:18 PM", "modernDateTime"));
    }

    @Test
    void modernDateTime_allFormatsFailUnparseable_passThrough() {
        assertEquals("not-a-datetime", new DateFormatter(fullConfig()).format("not-a-datetime", "modernDateTime"));
    }

    @Test
    void modernDateTime_blankValue_passThrough() {
        assertEquals("", new DateFormatter(fullConfig()).format("", "modernDateTime"));
    }

    @Test
    void modernDateTime_nullValue_passThrough() {
        assertNull(new DateFormatter(fullConfig()).format(null, "modernDateTime"));
    }

    @Test
    void modernDateTime_dateTimePairMissing_passThrough() {
        assertEquals("1/15/2024 09:30:00", new DateFormatter(datePairOnly()).format("1/15/2024 09:30:00", "modernDateTime"));
    }

    // --- unknown / non-date data types ---

    @Test
    void unknownType_text_passThrough() {
        assertEquals("someValue", new DateFormatter(fullConfig()).format("someValue", "text"));
    }

    @Test
    void unknownType_number_passThrough() {
        assertEquals("42", new DateFormatter(fullConfig()).format("42", "number"));
    }

    @Test
    void unknownType_nullDataType_passThrough() {
        assertEquals("someValue", new DateFormatter(fullConfig()).format("someValue", null));
    }

    @Test
    void unknownType_emptyDataType_passThrough() {
        assertEquals("someValue", new DateFormatter(fullConfig()).format("someValue", ""));
    }

    // --- format pattern variations ---

    @Test
    void genericDate_isoOutputFormat() {
        DateFormatConfig c = new DateFormatConfig();
        c.setInputFormats(List.of("M/d/yyyy"));
        c.setOutputFormat("yyyy-MM-dd");
        assertEquals("2024-01-15", new DateFormatter(c).format("1/15/2024", "genericDate"));
    }

    @Test
    void modernDateTime_isoOutputFormat() {
        DateFormatConfig c = new DateFormatConfig();
        c.setInputDateTimeFormats(List.of("M/d/yyyy h:mm:ss a", "M/d/yyyy HH:mm:ss"));
        c.setOutputDateTimeFormat("yyyy-MM-dd HH:mm:ss");
        assertEquals("2025-07-30 12:59:05", new DateFormatter(c).format("7/30/2025 12:59:05 PM", "modernDateTime"));
    }

    // --- multi-format: single-element list behaves same as single format ---

    @Test
    void modernDateTime_singleFormatList_reformatted() {
        DateFormatConfig c = new DateFormatConfig();
        c.setInputDateTimeFormats(List.of("M/d/yyyy HH:mm:ss"));
        c.setOutputDateTimeFormat("dd/MM/yyyy HH:mm:ss");
        assertEquals("15/01/2024 09:30:00", new DateFormatter(c).format("1/15/2024 09:30:00", "modernDateTime"));
    }

    @Test
    void modernDateTime_singleFormatList_noMatch_passThrough() {
        DateFormatConfig c = new DateFormatConfig();
        c.setInputDateTimeFormats(List.of("M/d/yyyy HH:mm:ss"));
        c.setOutputDateTimeFormat("dd/MM/yyyy HH:mm:ss");
        // AM/PM value doesn't match 24h-only format → pass through
        assertEquals("7/30/2025 12:59:05 PM", new DateFormatter(c).format("7/30/2025 12:59:05 PM", "modernDateTime"));
    }
}
