package com.clmextract.web.scheduler;

import com.clmextract.web.run.RunExecutor;
import com.clmextract.web.state.StateStore;
import com.clmextract.web.state.UiState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ExportScheduler {

    private static final Logger LOG = LogManager.getLogger(ExportScheduler.class);

    private final StateStore stateStore;
    private final RunExecutor runExecutor;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "export-scheduler");
                t.setDaemon(true);
                return t;
            });
    private ScheduledFuture<?> pending = null;

    public ExportScheduler(StateStore stateStore, RunExecutor runExecutor) {
        this.stateStore = stateStore;
        this.runExecutor = runExecutor;
    }

    /** Call once on server startup. Resumes any persisted active schedule. */
    public synchronized void start() {
        UiState.ScheduleState schedule = stateStore.read().getSchedule();
        if (!schedule.isEnabled()) return;

        // If the persisted nextRunAt is already in the past, fire immediately
        if (schedule.getNextRunAt() != null) {
            try {
                Instant persisted = Instant.parse(schedule.getNextRunAt());
                if (persisted.isBefore(Instant.now())) {
                    LOG.info("Scheduled run is overdue (was due at {}) — firing immediately", persisted);
                    pending = scheduler.schedule(this::fireRun, 0, TimeUnit.MILLISECONDS);
                    return;
                }
            } catch (Exception e) {
                LOG.warn("Could not parse persisted nextRunAt '{}', rescheduling normally", schedule.getNextRunAt());
            }
        }

        reschedule(schedule);
    }

    /** Cancel existing schedule and apply new settings. Call after PUT /api/schedule. */
    public synchronized void reschedule(UiState.ScheduleState schedule) {
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }

        if (!schedule.isEnabled()) {
            LOG.info("Auto-schedule disabled");
            return;
        }

        Instant nextRunAt = computeNextRunAt(schedule);

        // Persist nextRunAt into state
        UiState state = stateStore.read();
        state.getSchedule().setNextRunAt(nextRunAt.toString());
        stateStore.write(state);

        long delayMs = Math.max(0, nextRunAt.toEpochMilli() - System.currentTimeMillis());

        if (delayMs == 0) {
            LOG.info("Scheduled run is overdue — firing immediately");
        } else {
            LOG.info("Next scheduled run at {}", nextRunAt);
        }

        pending = scheduler.schedule(this::fireRun, delayMs, TimeUnit.MILLISECONDS);
    }

    /** Graceful shutdown — call on server stop (optional). */
    public void stop() {
        scheduler.shutdownNow();
    }

    private Instant computeNextRunAt(UiState.ScheduleState schedule) {
        ZoneId zone;
        try {
            zone = ZoneId.of(schedule.getTimezone() != null ? schedule.getTimezone() : "UTC");
        } catch (Exception e) {
            zone = ZoneId.of("UTC");
        }
        String[] tp = (schedule.getTimeOfDay() != null ? schedule.getTimeOfDay() : "02:00").split(":");
        int hour   = Integer.parseInt(tp[0]);
        int minute = tp.length > 1 ? Integer.parseInt(tp[1]) : 0;

        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime candidate = now.toLocalDate().atTime(hour, minute).atZone(zone);

        switch (schedule.getFrequency() != null ? schedule.getFrequency() : "DAILY") {
            case "WEEKLY": {
                DayOfWeek target = DayOfWeek.valueOf(
                        schedule.getDayOfWeek() != null ? schedule.getDayOfWeek() : "MONDAY");
                // Advance until we hit the right day AND the time is in the future
                while (candidate.getDayOfWeek() != target || !candidate.isAfter(now)) {
                    candidate = candidate.plusDays(1);
                }
                break;
            }
            case "MONTHLY": {
                // Same time, first day of next month (or this month if still in future)
                candidate = now.withDayOfMonth(1).withHour(hour).withMinute(minute)
                               .withSecond(0).withNano(0);
                if (!candidate.isAfter(now)) {
                    candidate = candidate.plusMonths(1);
                }
                break;
            }
            default: // DAILY
                if (!candidate.isAfter(now)) {
                    candidate = candidate.plusDays(1);
                }
                break;
        }
        return candidate.toInstant();
    }

    private void fireRun() {
        LOG.info("ExportScheduler firing scheduled run");
        UiState state = stateStore.read();
        UiState.ScheduleState schedule = state.getSchedule();
        List<String> selectedBos = schedule.getSelectedBos() != null
                ? schedule.getSelectedBos() : List.of();
        String sftpPath = state.getSftpTargetPath() != null ? state.getSftpTargetPath() : "";
        try {
            runExecutor.startRun(selectedBos, sftpPath, null, null);
        } catch (Exception e) {
            LOG.error("Scheduled run failed to start: {}", e.getMessage(), e);
        }
        // Reschedule for next occurrence
        if (schedule.isEnabled()) {
            reschedule(schedule);
        }
    }
}
