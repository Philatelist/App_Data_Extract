package com.clmextract.csv;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilenameResolverTest {

    @Test
    void testAllPlaceholdersResolved() {
        FilenameResolver resolver = new FilenameResolver("15032026", "143022");
        String result = resolver.resolve(
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv", "Contract", "Summary");

        assertEquals("Contract_Summary_15032026_143022.csv", result);
    }

    @Test
    void testBoPlaceholder() {
        FilenameResolver resolver = new FilenameResolver("01012026", "000000");
        String result = resolver.resolve("{BO}_export.csv", "Amendment", null);

        assertEquals("Amendment_export.csv", result);
    }

    @Test
    void testComponentPlaceholder() {
        FilenameResolver resolver = new FilenameResolver("01012026", "000000");
        String result = resolver.resolve("{Component}_data.csv", null, "Details");

        assertEquals("Details_data.csv", result);
    }

    @Test
    void testDateAndTimePlaceholders() {
        FilenameResolver resolver = new FilenameResolver("25122026", "235959");
        String result = resolver.resolve("export_{DDMMYYYY}_{HHMMSS}.csv", "BO", "Comp");

        assertEquals("export_25122026_235959.csv", result);
    }

    @Test
    void testNoPlaceholders() {
        FilenameResolver resolver = new FilenameResolver("01012026", "000000");
        String result = resolver.resolve("static_filename.csv", "Contract", "Summary");

        assertEquals("static_filename.csv", result);
    }

    @Test
    void testPerBoOverrideTemplate() {
        FilenameResolver resolver = new FilenameResolver("01012026", "120000");
        String globalTemplate = "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv";
        String perBoTemplate = "{BO}_custom_{DDMMYYYY}.csv";

        String globalResult = resolver.resolve(globalTemplate, "Contract", "Summary");
        String perBoResult = resolver.resolve(perBoTemplate, "Contract", "Summary");

        assertEquals("Contract_Summary_01012026_120000.csv", globalResult);
        assertEquals("Contract_custom_01012026.csv", perBoResult);
    }

    @Test
    void testTimestampCapturedOnce() {
        FilenameResolver resolver = new FilenameResolver("01012026", "120000");

        String result1 = resolver.resolve("{DDMMYYYY}_{HHMMSS}.csv", "A", "B");
        String result2 = resolver.resolve("{DDMMYYYY}_{HHMMSS}.csv", "C", "D");

        // Both should have the same timestamp
        assertEquals("01012026_120000.csv", result1);
        assertEquals("01012026_120000.csv", result2);
    }

    @Test
    void testDefaultConstructorProducesValidTimestamp() {
        FilenameResolver resolver = new FilenameResolver();
        String result = resolver.resolve("{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                "Contract", "Summary");

        // Should not contain placeholder tokens
        assertFalse(result.contains("{BO}"));
        assertFalse(result.contains("{Component}"));
        assertFalse(result.contains("{DDMMYYYY}"));
        assertFalse(result.contains("{HHMMSS}"));
        assertTrue(result.startsWith("Contract_Summary_"));
        assertTrue(result.endsWith(".csv"));
    }
}
