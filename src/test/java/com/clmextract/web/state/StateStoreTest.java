package com.clmextract.web.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StateStoreTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void jsonRoundTrip(@TempDir Path dir) {
        Path file = dir.resolve("ui-state.json");
        StateStore store = new StateStore(file, mapper);

        UiState state = store.read();
        state.getBoLastRun().put("TestBO", "2026-01-01T00:00:00Z");
        store.write(state);

        StateStore store2 = new StateStore(file, mapper);
        assertEquals("2026-01-01T00:00:00Z", store2.read().getBoLastRun().get("TestBO"));
    }

    @Test
    void malformedJsonRecoversFreshState(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("ui-state.json");
        Files.writeString(file, "not valid json {{{{");

        StateStore store = new StateStore(file, mapper);
        assertNotNull(store.read());
        assertNotNull(store.read().getBoLastRun());
        assertTrue(store.read().getBoLastRun().isEmpty());
    }

    @Test
    void missingFileReturnsFreshState(@TempDir Path dir) {
        Path file = dir.resolve("ui-state.json");
        StateStore store = new StateStore(file, mapper);
        assertNotNull(store.read());
        assertTrue(store.read().getBoLastRun().isEmpty());
    }
}
