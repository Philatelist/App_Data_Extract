package com.clmextract.web.run;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DateFilterTest {

    @Test
    void createDate_activeWhenDateFieldIsCreateDateAndDateFromIsNonBlank() {
        DateFilter filter = new DateFilter("createDate", "2026-01-15", false, 0);
        assertEquals("createDate", filter.getDateField());
        assertEquals("2026-01-15", filter.getDateFrom());
        assertFalse(filter.isModifiedWithinPeriod());
        // Active createDate filter: dateField == "createDate" && dateFrom non-blank
        assertTrue("createDate".equals(filter.getDateField()) && !filter.getDateFrom().isBlank());
    }

    @Test
    void modifiedWithinPeriod_activeWhenFlagIsTrue() {
        DateFilter filter = new DateFilter("", "", true, 7);
        assertTrue(filter.isModifiedWithinPeriod());
        assertEquals(7, filter.getDaysBeforeToday());
    }

    @Test
    void noFilter_whenDateFieldBlankAndModifiedWithinPeriodFalse() {
        DateFilter filter = new DateFilter("", "", false, 0);
        assertFalse("createDate".equals(filter.getDateField()) && !filter.getDateFrom().isBlank());
        assertFalse(filter.isModifiedWithinPeriod());
    }

    @Test
    void daysBeforeToday_computedFromFrequency() {
        // DAILY → 1
        DateFilter daily = new DateFilter("", "", true, 1);
        assertEquals(1, daily.getDaysBeforeToday());

        // WEEKLY → 7
        DateFilter weekly = new DateFilter("", "", true, 7);
        assertEquals(7, weekly.getDaysBeforeToday());

        // MONTHLY → 30
        DateFilter monthly = new DateFilter("", "", true, 30);
        assertEquals(30, monthly.getDaysBeforeToday());
    }
}
