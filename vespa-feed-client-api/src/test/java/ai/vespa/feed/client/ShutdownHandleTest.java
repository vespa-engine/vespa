// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.junit.jupiter.api.Test;

import static ai.vespa.feed.client.ShutdownHandle.Phase.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShutdownHandleTest {
    @Test
    void snapshotRejectsContradictoryOutcomeAndOwnership() {
        // Without validation, callers can be told both "terminated" and "still owns work".
        Throwable failure = new IllegalStateException("cleanup failed");
        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new ShutdownHandle.Snapshot(null, null, 0, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShutdownHandle.Snapshot(OPEN, failure, 0, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShutdownHandle.Snapshot(FAILED, null, 0, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShutdownHandle.Snapshot(OPEN, null, -1, 0)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShutdownHandle.Snapshot(OPEN, null, 0, -1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShutdownHandle.Snapshot(DRAINING, null, 2, 1)),
                () -> assertThrows(IllegalArgumentException.class, () -> new ShutdownHandle.Snapshot(TERMINATED, null, 0, 1)),
                () -> assertDoesNotThrow(() -> new ShutdownHandle.Snapshot(FAILED, failure, 1, 2)),
                () -> assertDoesNotThrow(() -> new ShutdownHandle.Snapshot(TERMINATED, null, 0, 0))
        );
    }
}
