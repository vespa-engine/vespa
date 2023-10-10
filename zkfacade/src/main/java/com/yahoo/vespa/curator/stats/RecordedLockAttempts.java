// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Contains information about the lock attempts made by a thread between a start and end.
 *
 * <p>Any thread is allowed access the public methods of an instance.</p>
 *
 * @author hakon
 */
public class RecordedLockAttempts {
    private static final int MAX_TOP_LEVEL_LOCK_ATTEMPTS = 100;

    private final String recordId;
    private final Instant startInstant;
    private volatile Instant endInstant = null;
    private final ConcurrentLinkedQueue<LockAttempt> lockAttempts = new ConcurrentLinkedQueue<>();

    static RecordedLockAttempts startRecording(String recordId) {
        return new RecordedLockAttempts(recordId);
    }

    private RecordedLockAttempts(String recordId) {
        this.recordId = recordId;
        startInstant = Instant.now();
    }

    /** Note: A LockAttempt may have nested lock attempts. */
    void addTopLevelLockAttempt(LockAttempt lockAttempt) {
        // guard against recordings that are too long - to cap the memory used
        if (lockAttempts.size() < MAX_TOP_LEVEL_LOCK_ATTEMPTS) {
            lockAttempts.add(lockAttempt);
        }
    }

    void stopRecording() {
        endInstant = Instant.now();
    }

    public String recordId() { return recordId; }
    public Instant startInstant() { return startInstant; }
    public Instant endInstant() { return endInstant == null ? Instant.now() : endInstant; }
    public Duration duration() { return Duration.between(startInstant, endInstant()); }
    public List<LockAttempt> lockAttempts() { return List.copyOf(lockAttempts); }
}
