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

    // ── sanitize() tests ────────────────────────────────────────────────

    @Test
    void testSanitizeCleanStringUnchanged() {
        assertEquals("Summary", FilenameResolver.sanitize("Summary"));
        assertEquals("NAFBO", FilenameResolver.sanitize("NAFBO"));
        assertEquals("Merged", FilenameResolver.sanitize("Merged"));
    }

    @Test
    void testSanitizeSpacesReplacedWithUnderscore() {
        assertEquals("Financial_Reporting_and_Control_Review",
                FilenameResolver.sanitize("Financial Reporting and Control Review"));
    }

    @Test
    void testSanitizeSpecialCharacters() {
        assertEquals("Cost_Budget_Review",
                FilenameResolver.sanitize("Cost & Budget (Review)"));
    }

    @Test
    void testSanitizeNullAndEmpty() {
        assertEquals("", FilenameResolver.sanitize(null));
        assertEquals("", FilenameResolver.sanitize(""));
    }

    @Test
    void testSanitizeCollapseAndTrim() {
        assertEquals("test", FilenameResolver.sanitize("___test___"));
        assertEquals("a_b", FilenameResolver.sanitize("a___b"));
        assertEquals("test", FilenameResolver.sanitize("...test..."));
    }

    @Test
    void testResolveWithSanitization() {
        FilenameResolver resolver = new FilenameResolver("01012026", "120000");
        String result = resolver.resolve(
                "{BO}_{Component}_{DDMMYYYY}_{HHMMSS}.csv",
                "NAFBO",
                "Financial Reporting and Control Review");

        assertEquals("NAFBO_Financial_Reporting_and_Control_Review_01012026_120000.csv", result);
    }
}
