package com.clmextract.export;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrackingFilterTest {

    @Test
    void testSingleIds() {
        List<Long> all = Arrays.asList(1000L, 2000L, 3050L, 4000L, 5000L);
        List<Long> result = TrackingFilter.apply(all, "3050, 5000");

        assertEquals(2, result.size());
        assertEquals(3050L, result.get(0));
        assertEquals(5000L, result.get(1));
    }

    @Test
    void testRange() {
        List<Long> all = Arrays.asList(999L, 1000L, 1500L, 2000L, 2001L);
        List<Long> result = TrackingFilter.apply(all, "1000-2000");

        assertEquals(3, result.size());
        assertEquals(1000L, result.get(0));
        assertEquals(1500L, result.get(1));
        assertEquals(2000L, result.get(2));
    }

    @Test
    void testCombinedRangesAndIds() {
        List<Long> all = Arrays.asList(500L, 1000L, 1500L, 2000L, 3050L, 4000L, 4500L, 5000L);
        List<Long> result = TrackingFilter.apply(all, "1000-2000, 3050, 4000-4500");

        assertEquals(6, result.size());
        assertTrue(result.contains(1000L));
        assertTrue(result.contains(1500L));
        assertTrue(result.contains(2000L));
        assertTrue(result.contains(3050L));
        assertTrue(result.contains(4000L));
        assertTrue(result.contains(4500L));
    }

    @Test
    void testEmptyFilterReturnsAll() {
        List<Long> all = Arrays.asList(1000L, 2000L, 3000L);

        List<Long> result1 = TrackingFilter.apply(all, "");
        assertEquals(3, result1.size());

        List<Long> result2 = TrackingFilter.apply(all, null);
        assertEquals(3, result2.size());

        List<Long> result3 = TrackingFilter.apply(all, "  ");
        assertEquals(3, result3.size());
    }

    @Test
    void testNonMatchingIdsSkipped() {
        List<Long> all = Arrays.asList(1000L, 2000L, 3000L);
        List<Long> result = TrackingFilter.apply(all, "9999, 8888");

        assertEquals(0, result.size());
    }

    @Test
    void testMixedMatchAndNonMatch() {
        List<Long> all = Arrays.asList(1000L, 2000L, 3000L);
        List<Long> result = TrackingFilter.apply(all, "1000, 9999, 3000");

        assertEquals(2, result.size());
        assertEquals(1000L, result.get(0));
        assertEquals(3000L, result.get(1));
    }

    @Test
    void testRangeInclusiveBoundaries() {
        List<Long> all = Arrays.asList(99L, 100L, 105L, 110L, 111L);
        List<Long> result = TrackingFilter.apply(all, "100-110");

        assertEquals(3, result.size());
        assertTrue(result.contains(100L));
        assertTrue(result.contains(105L));
        assertTrue(result.contains(110L));
        assertFalse(result.contains(99L));
        assertFalse(result.contains(111L));
    }

    @Test
    void testSingleIdAsOnlyFilter() {
        List<Long> all = Arrays.asList(1000L, 2000L, 3000L);
        List<Long> result = TrackingFilter.apply(all, "2000");

        assertEquals(1, result.size());
        assertEquals(2000L, result.get(0));
    }

    @Test
    void testPreservesOriginalOrder() {
        List<Long> all = Arrays.asList(5000L, 3000L, 1000L, 4000L, 2000L);
        List<Long> result = TrackingFilter.apply(all, "1000-5000");

        assertEquals(5, result.size());
        assertEquals(5000L, result.get(0));
        assertEquals(3000L, result.get(1));
        assertEquals(1000L, result.get(2));
        assertEquals(4000L, result.get(3));
        assertEquals(2000L, result.get(4));
    }
}
