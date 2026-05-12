package com.clmextract.web.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class StateStore {

    private static final Logger LOG = LogManager.getLogger(StateStore.class);

    private final Path stateFile;
    private final ObjectMapper mapper;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private UiState state;

    public StateStore(Path stateFile, ObjectMapper mapper) {
        this.stateFile = stateFile;
        this.mapper = mapper;
        this.state = loadFromDisk();
    }

    private UiState loadFromDisk() {
        if (!Files.exists(stateFile)) return new UiState();
        try {
            return mapper.readValue(stateFile.toFile(), UiState.class);
        } catch (Exception e) {
            LOG.warn("ui-state.json malformed, starting fresh: {}", e.getMessage());
            return new UiState();
        }
    }

    public UiState read() {
        lock.readLock().lock();
        try { return state; }
        finally { lock.readLock().unlock(); }
    }

    public void write(UiState newState) {
        lock.writeLock().lock();
        try {
            Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
            mapper.writeValue(tmp.toFile(), newState);
            Files.move(tmp, stateFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            this.state = newState;
        } catch (IOException e) {
            LOG.error("Failed to write ui-state.json: {}", e.getMessage(), e);
            throw new RuntimeException("State write failed", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
