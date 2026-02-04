package com.clmextract.export;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

        Set<Long> allowedIds = parseFilter(filterString);
        List<Long> filtered = trackingIds.stream()
                .filter(allowedIds::contains)
                .collect(Collectors.toList());

        logger.info("Tracking filter applied: {} -> {} IDs (filter: {})",
                trackingIds.size(), filtered.size(), filterString);
        return filtered;
    }

    static Set<Long> parseFilter(String filterString) {
        Set<Long> allowed = new HashSet<>();
        String[] segments = filterString.split(",");

        for (String segment : segments) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.contains("-")) {
                String[] parts = trimmed.split("-", 2);
                try {
                    long start = Long.parseLong(parts[0].trim());
                    long end = Long.parseLong(parts[1].trim());
                    for (long i = start; i <= end; i++) {
                        allowed.add(i);
                    }
                } catch (NumberFormatException e) {
                    logger.warn("Invalid range in tracking filter: {}", trimmed);
                }
            } else {
                try {
                    allowed.add(Long.parseLong(trimmed));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid ID in tracking filter: {}", trimmed);
                }
            }
        }

        return allowed;
    }
}
