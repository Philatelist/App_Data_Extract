package com.clmextract.web.scheduler;

import com.clmextract.web.run.DateFilter;
import com.clmextract.web.run.RunExecutor;
import com.clmextract.web.state.StateStore;
import com.clmextract.web.state.UiState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExportSchedulerTest {

    @TempDir Path tempDir;

    StateStore stateStore;
    StubRunExecutor runExecutor;
    ExportScheduler scheduler;

    @BeforeEach
    void setUp() {
        stateStore = new StateStore(tempDir.resolve("ui-state.json"), new ObjectMapper());
        runExecutor = new StubRunExecutor(stateStore);
        scheduler = new ExportScheduler(stateStore, runExecutor);
    }

    @Test
    void reschedule_persistsNextRunAt() {
        UiState.ScheduleState sched = new UiState.ScheduleState();
        sched.setEnabled(true);
        sched.setFrequency("DAILY");
        sched.setTimeOfDay("23:59");
        sched.setTimezone("UTC");

        scheduler.reschedule(sched);

        String nextRunAt = stateStore.read().getSchedule().getNextRunAt();
        assertNotNull(nextRunAt, "nextRunAt should be persisted after reschedule");
        assertTrue(Instant.parse(nextRunAt).isAfter(Instant.now()),
                "nextRunAt should be in the future");
    }

    @Test
    void reschedule_disabledSchedule_doesNotPersistNextRunAt() {
        UiState.ScheduleState sched = new UiState.ScheduleState();
        sched.setEnabled(false);

        scheduler.reschedule(sched);

        // nextRunAt should remain null (schedule never persists nextRunAt when disabled)
        assertNull(stateStore.read().getSchedule().getNextRunAt());
        assertEquals(0, runExecutor.startCount.get());
    }

    @Test
    void reschedule_calledTwice_updatesNextRunAt() throws InterruptedException {
        UiState.ScheduleState first = new UiState.ScheduleState();
        first.setEnabled(true);
        first.setFrequency("WEEKLY");
        first.setDayOfWeek("MONDAY");
        first.setTimeOfDay("06:00");
        first.setTimezone("UTC");
        scheduler.reschedule(first);
        String firstNextRun = stateStore.read().getSchedule().getNextRunAt();

        UiState.ScheduleState second = new UiState.ScheduleState();
        second.setEnabled(true);
        second.setFrequency("MONTHLY");
        second.setTimeOfDay("08:00");
        second.setTimezone("UTC");
        scheduler.reschedule(second);
        String secondNextRun = stateStore.read().getSchedule().getNextRunAt();

        assertNotNull(firstNextRun);
        assertNotNull(secondNextRun);
        // Monthly next-run differs from weekly next-run
        assertNotEquals(firstNextRun, secondNextRun, "Second reschedule should update nextRunAt");
    }

    @Test
    void start_overdueNextRunAt_firesImmediately() throws InterruptedException {
        // Persist an enabled schedule with nextRunAt in the past
        UiState state = stateStore.read();
        UiState.ScheduleState sched = state.getSchedule();
        sched.setEnabled(true);
        sched.setFrequency("DAILY");
        sched.setTimeOfDay("02:00");
        sched.setTimezone("UTC");
        sched.setNextRunAt(Instant.now().minusSeconds(3600).toString()); // 1 hour ago
        state.setSchedule(sched);
        stateStore.write(state);

        scheduler.start();

        // Allow time for the immediate fire
        Thread.sleep(500);
        assertTrue(runExecutor.started.get(), "startRun should be called immediately when nextRunAt is overdue");
    }

    @Test
    void start_futureNextRunAt_doesNotFireImmediately() throws InterruptedException {
        UiState state = stateStore.read();
        UiState.ScheduleState sched = state.getSchedule();
        sched.setEnabled(true);
        sched.setFrequency("DAILY");
        sched.setTimeOfDay("02:00");
        sched.setTimezone("UTC");
        sched.setNextRunAt(Instant.now().plusSeconds(86400).toString()); // tomorrow
        state.setSchedule(sched);
        stateStore.write(state);

        scheduler.start();

        Thread.sleep(200);
        assertFalse(runExecutor.started.get(), "startRun should NOT fire immediately when nextRunAt is in the future");
    }

    // ─── Stub ─────────────────────────────────────────────────────────────────

    static class StubRunExecutor extends RunExecutor {
        final AtomicBoolean started = new AtomicBoolean(false);
        final AtomicInteger startCount = new AtomicInteger(0);

        StubRunExecutor(StateStore stateStore) {
            super("", stateStore);
        }

        @Override
        public boolean startRun(List<String> selectedBos, String sftpTargetPath, String clmSessionId, DateFilter dateFilter) {
            started.set(true);
            startCount.incrementAndGet();
            return true;
        }
    }
}
