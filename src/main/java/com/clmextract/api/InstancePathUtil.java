package com.clmextract.api;

import java.util.ArrayList;
import java.util.List;

public final class InstancePathUtil {

    private InstancePathUtil() {}

    public static ParsedPath parse(String instancePath) {
        if (instancePath == null || instancePath.isBlank()) {
            return ParsedPath.empty();
        }

        String s = instancePath.trim();

        // Strip known prefixes
        if (s.startsWith("MCPDef:/")) {
            s = s.substring("MCPDef:/".length());
        } else if (s.startsWith("MCP:/")) {
            s = s.substring("MCP:/".length());
        } else if (s.startsWith("BundleDef:/")) {
            s = s.substring("BundleDef:/".length());
        }

        String[] raw = s.split("/");
        List<String> segments = new ArrayList<>();
        List<String> instanceIds = new ArrayList<>();

        for (String r : raw) {
            if (r == null || r.isBlank()) {
                continue;
            }
            int pipeIdx = r.indexOf('|');
            if (pipeIdx >= 0) {
                segments.add(r.substring(0, pipeIdx));
                instanceIds.add(r.substring(pipeIdx + 1));
            } else {
                segments.add(r);
                instanceIds.add(null);
            }
        }

        if (segments.isEmpty()) {
            return ParsedPath.empty();
        }

        String module = segments.get(0);
        String component = segments.size() >= 2 ? segments.get(1) : null;
        String parameter = segments.size() >= 3 ? segments.get(segments.size() - 1) : null;
        String componentInstanceId = segments.size() >= 2 ? instanceIds.get(1) : null;

        return new ParsedPath(module, component, parameter, componentInstanceId);
    }

    public record ParsedPath(String module, String component, String parameter,
                              String componentInstanceId) {
        public static ParsedPath empty() {
            return new ParsedPath(null, null, null, null);
        }

        public boolean isEmpty() {
            return module == null;
        }
    }
}
