package com.clmextract.export;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TrackingFilter {

    private static final Logger logger = LogManager.getLogger(TrackingFilter.class);

    public static List<Long> apply(List<Long> trackingIds, String filterString) {
        if (filterString == null || filterString.trim().isEmpty()) {
            return trackingIds;
        }

        ParsedFilter filter = parseFilter(filterString);
        List<Long> filtered = trackingIds.stream()
                .filter(filter::matches)
                .collect(Collectors.toList());

        logger.info("Tracking filter applied: {} -> {} IDs (filter: {})",
                trackingIds.size(), filtered.size(), filterString);
        return filtered;
    }

    static ParsedFilter parseFilter(String filterString) {
        Set<Long> exactIds = new HashSet<>();
        List<long[]> ranges = new ArrayList<>();

        for (String segment : filterString.split(",")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) continue;

            if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-", 2);
                try {
                    long start = Long.parseLong(parts[0].trim());
                    long end = Long.parseLong(parts[1].trim());
                    ranges.add(new long[]{Math.min(start, end), Math.max(start, end)});
                } catch (NumberFormatException e) {
                    logger.warn("Invalid range in tracking filter: {}", trimmed);
                }
            } else {
                try {
                    exactIds.add(Long.parseLong(trimmed));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid ID in tracking filter: {}", trimmed);
                }
            }
        }

        return new ParsedFilter(exactIds, ranges);
    }

    static class ParsedFilter {
        private final Set<Long> exactIds;
        private final List<long[]> ranges;

        ParsedFilter(Set<Long> exactIds, List<long[]> ranges) {
            this.exactIds = exactIds;
            this.ranges = ranges;
        }

        boolean matches(long id) {
            if (exactIds.contains(id)) return true;
            for (long[] range : ranges) {
                if (id >= range[0] && id <= range[1]) return true;
            }
            return false;
        }
    }
}
