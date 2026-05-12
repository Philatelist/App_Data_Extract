package com.clmextract.web.api;

import com.clmextract.config.AppConfig;
import com.clmextract.config.BoTypeConfig;
import com.clmextract.config.ConfigLoader;
import com.clmextract.web.run.DateFilter;
import com.clmextract.web.run.RunExecutor;
import com.clmextract.web.scheduler.ExportScheduler;
import com.clmextract.web.state.StateStore;
import com.clmextract.web.state.UiState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RunController {

    private final String configPath;
    private final StateStore stateStore;
    private final RunExecutor runExecutor;
    private final ObjectMapper objectMapper;
    private final ExportScheduler exportScheduler;

    public RunController(String configPath, StateStore stateStore, RunExecutor runExecutor,
                         ObjectMapper objectMapper, ExportScheduler exportScheduler) {
        this.configPath = configPath;
        this.stateStore = stateStore;
        this.runExecutor = runExecutor;
        this.objectMapper = objectMapper;
        this.exportScheduler = exportScheduler;
    }

    public void getBos(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"OPERATOR".equals(role)) { ctx.status(403).result("Forbidden"); return; }

        AppConfig config = ConfigLoader.loadRaw(configPath);
        UiState state = stateStore.read();
        Map<String, String> lastRun = state.getBoLastRun();

        List<Map<String, Object>> result = new ArrayList<>();
        for (BoTypeConfig bt : config.getBoTypes()) {
            String name = bt.getName();
            if (name == null || name.isEmpty()) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", name);
            entry.put("localizedName", bt.getLocalizedName() != null && !bt.getLocalizedName().isEmpty()
                    ? bt.getLocalizedName() : name);
            entry.put("lastRunDate", lastRun.getOrDefault(name, null));
            result.add(entry);
        }

        ctx.contentType("application/json");
        ctx.result(objectMapper.writeValueAsString(result));
    }

    public void startRun(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"OPERATOR".equals(role)) { ctx.status(403).result("Forbidden"); return; }

        JsonNode body = objectMapper.readTree(ctx.body());
        List<String> boNames = new ArrayList<>();
        if (body.has("boNames")) {
            body.get("boNames").forEach(n -> boNames.add(n.asText()));
        }
        String sftpTargetPath = body.has("sftpTargetPath") ? body.get("sftpTargetPath").asText("") : "";

        String clmSessionId = ctx.sessionAttribute("clmSessionId");

        // Build DateFilter from request body
        DateFilter dateFilter = null;
        String dateField = body.has("dateField") ? body.get("dateField").asText("") : "";
        String dateFrom = body.has("dateFrom") ? body.get("dateFrom").asText("") : "";
        boolean modifiedWithinPeriod = body.has("modifiedWithinPeriod") && body.get("modifiedWithinPeriod").asBoolean(false);

        if (("createDate".equals(dateField) && !dateFrom.isBlank()) || modifiedWithinPeriod) {
            int daysBeforeToday = 1;
            if (modifiedWithinPeriod) {
                String frequency = stateStore.read().getSchedule().getFrequency();
                if ("WEEKLY".equals(frequency)) daysBeforeToday = 7;
                else if ("MONTHLY".equals(frequency)) daysBeforeToday = 30;
            }
            dateFilter = new DateFilter(dateField, dateFrom, modifiedWithinPeriod, daysBeforeToday);
        }

        boolean started = runExecutor.startRun(boNames, sftpTargetPath, clmSessionId, dateFilter);
        if (!started) {
            ctx.status(409).result("{\"error\":\"Export already running\"}");
            return;
        }
        String runId = stateStore.read().getCurrentRun().getRunId();
        ctx.contentType("application/json");
        ctx.result("{\"runId\":\"" + runId + "\"}");
    }

    public void getRunHistory(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"OPERATOR".equals(role)) { ctx.status(403).result("Forbidden"); return; }
        ctx.contentType("application/json");
        ctx.result(objectMapper.writeValueAsString(stateStore.read().getRunHistory()));
    }

    public void getRunStatus(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"OPERATOR".equals(role)) { ctx.status(403).result("Forbidden"); return; }
        UiState state = stateStore.read();
        ctx.contentType("application/json");
        ctx.result(objectMapper.writeValueAsString(state.getCurrentRun()));
    }

    public void stopRun(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"OPERATOR".equals(role)) { ctx.status(403).result("Forbidden"); return; }
        boolean stopped = runExecutor.stopRun();
        ctx.contentType("application/json");
        ctx.result(stopped ? "{\"stopped\":true}" : "{\"stopped\":false}");
    }

    public void getSchedule(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"OPERATOR".equals(role)) { ctx.status(403).result("Forbidden"); return; }
        ctx.contentType("application/json");
        ctx.result(objectMapper.writeValueAsString(stateStore.read().getSchedule()));
    }

    public void deleteSchedule(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"OPERATOR".equals(role)) { ctx.status(403).result("Forbidden"); return; }
        UiState state = stateStore.read();
        state.setSchedule(new UiState.ScheduleState());
        stateStore.write(state);
        if (exportScheduler != null) {
            exportScheduler.reschedule(state.getSchedule()); // disabled by default → cancels
        }
        ctx.status(200).result("{\"ok\":true}");
    }

    public void putSchedule(Context ctx) throws Exception {
        String role = ctx.sessionAttribute("role");
        if (!"OPERATOR".equals(role)) { ctx.status(403).result("Forbidden"); return; }

        com.fasterxml.jackson.databind.JsonNode body = objectMapper.readTree(ctx.body());
        UiState state = stateStore.read();
        UiState.ScheduleState schedule = state.getSchedule();

        if (body.has("enabled"))               schedule.setEnabled(body.get("enabled").asBoolean(false));
        if (body.has("frequency"))             schedule.setFrequency(body.get("frequency").asText("DAILY"));
        if (body.has("dayOfWeek"))             schedule.setDayOfWeek(body.get("dayOfWeek").asText("MONDAY"));
        if (body.has("timeOfDay"))             schedule.setTimeOfDay(body.get("timeOfDay").asText("02:00"));
        if (body.has("timezone"))              schedule.setTimezone(body.get("timezone").asText("UTC"));
        if (body.has("dateField"))             schedule.setDateField(body.get("dateField").asText(""));
        if (body.has("dateFrom"))              schedule.setDateFrom(body.get("dateFrom").asText(""));
        if (body.has("modifiedWithinPeriod"))  schedule.setModifiedWithinPeriod(body.get("modifiedWithinPeriod").asBoolean(false));

        if (body.has("selectedBos") && body.get("selectedBos").isArray()) {
            java.util.List<String> bos = new java.util.ArrayList<>();
            body.get("selectedBos").forEach(n -> bos.add(n.asText()));
            schedule.setSelectedBos(bos);
        }

        state.setSchedule(schedule);
        stateStore.write(state);

        if (exportScheduler != null) {
            exportScheduler.reschedule(state.getSchedule());
        }

        ctx.status(200).result("{\"ok\":true}");
    }
}
