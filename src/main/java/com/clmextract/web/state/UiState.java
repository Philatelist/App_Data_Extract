package com.clmextract.web.state;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UiState {

    private Map<String, String> boLastRun = new HashMap<>();
    private ScheduleState schedule = new ScheduleState();
    private String sftpTargetPath = "";
    private RunState currentRun = null;
    private List<RunState> runHistory = new ArrayList<>();
    private List<Map<String, Object>> cachedBoTypes = new ArrayList<>();

    public Map<String, String> getBoLastRun() { return boLastRun; }
    public void setBoLastRun(Map<String, String> boLastRun) { this.boLastRun = boLastRun; }

    public ScheduleState getSchedule() { return schedule; }
    public void setSchedule(ScheduleState schedule) { this.schedule = schedule; }

    public String getSftpTargetPath() { return sftpTargetPath; }
    public void setSftpTargetPath(String sftpTargetPath) { this.sftpTargetPath = sftpTargetPath; }

    public RunState getCurrentRun() { return currentRun; }
    public void setCurrentRun(RunState currentRun) { this.currentRun = currentRun; }

    public List<RunState> getRunHistory() { return runHistory; }
    public void setRunHistory(List<RunState> runHistory) { this.runHistory = runHistory; }

    public List<Map<String, Object>> getCachedBoTypes() { return cachedBoTypes; }
    public void setCachedBoTypes(List<Map<String, Object>> cachedBoTypes) {
        this.cachedBoTypes = cachedBoTypes != null ? cachedBoTypes : new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScheduleState {
        private String frequency = "DAILY";
        private boolean enabled = false;
        private String timeOfDay = "02:00";
        private String nextRunAt = null;
        private String dayOfWeek = "MONDAY";
        private String timezone = "UTC";
        private java.util.List<String> selectedBos = new java.util.ArrayList<>();
        private String dateField = "";
        private String dateFrom = "";
        private boolean modifiedWithinPeriod = false;

        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getTimeOfDay() { return timeOfDay; }
        public void setTimeOfDay(String timeOfDay) { this.timeOfDay = timeOfDay; }

        public String getNextRunAt() { return nextRunAt; }
        public void setNextRunAt(String nextRunAt) { this.nextRunAt = nextRunAt; }

        public String getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

        public String getTimezone() { return timezone; }
        public void setTimezone(String timezone) { this.timezone = timezone; }

        public java.util.List<String> getSelectedBos() { return selectedBos; }
        public void setSelectedBos(java.util.List<String> selectedBos) { this.selectedBos = selectedBos; }

        public String getDateField() { return dateField; }
        public void setDateField(String dateField) { this.dateField = dateField; }

        public String getDateFrom() { return dateFrom; }
        public void setDateFrom(String dateFrom) { this.dateFrom = dateFrom; }

        public boolean isModifiedWithinPeriod() { return modifiedWithinPeriod; }
        public void setModifiedWithinPeriod(boolean modifiedWithinPeriod) { this.modifiedWithinPeriod = modifiedWithinPeriod; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RunState {
        private String runId;
        private String startedAt;
        private String completedAt = null;
        private List<String> selectedBos = new ArrayList<>();
        private Map<String, String> steps = new LinkedHashMap<>();
        private List<String> warnings = new ArrayList<>();

        public String getRunId() { return runId; }
        public void setRunId(String runId) { this.runId = runId; }

        public String getStartedAt() { return startedAt; }
        public void setStartedAt(String startedAt) { this.startedAt = startedAt; }

        public String getCompletedAt() { return completedAt; }
        public void setCompletedAt(String completedAt) { this.completedAt = completedAt; }

        public List<String> getSelectedBos() { return selectedBos; }
        public void setSelectedBos(List<String> selectedBos) { this.selectedBos = selectedBos; }

        public Map<String, String> getSteps() { return steps; }
        public void setSteps(Map<String, String> steps) { this.steps = steps; }

        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
    }
}
