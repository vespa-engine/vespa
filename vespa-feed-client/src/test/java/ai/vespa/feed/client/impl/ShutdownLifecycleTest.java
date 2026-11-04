// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client.impl;

import ai.vespa.feed.client.ShutdownHandle;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShutdownLifecycleTest {
    @Test
    void generatedHandoffHistoriesPreserveOwnershipAndMonotonicShutdown() {
        // Removing resolution ownership or permitting ABORTING -> DRAINING must break these properties.
        // This is a sequential component state-machine test, not a JVM concurrency stress claim.
        for (int seed = 0; seed < 64; seed++) {
            ShutdownLifecycle lifecycle = new ShutdownLifecycle(() -> false);
            Random random = new Random(seed);
            List<TrackedOperation> operations = new ArrayList<>();
            for (int initial = 0; initial < 3; initial++) operations.add(new TrackedOperation(lifecycle.admit()));
            StringBuilder history = new StringBuilder("seed=").append(seed);
            ShutdownHandle.Phase previous = ShutdownHandle.Phase.OPEN;
            for (int step = 0; step < 100; step++) {
                int command = random.nextInt(6);
                int index = operations.isEmpty() ? -1 : random.nextInt(operations.size());
                history.append("\n").append(step).append(": command=").append(command).append(" operation=").append(index);
                TrackedOperation operation = index < 0 ? null : operations.get(index);
                switch (command) {
                    case 0:
                        if (previous == ShutdownHandle.Phase.OPEN) operations.add(new TrackedOperation(lifecycle.admit()));
                        else assertThrows(IllegalStateException.class, lifecycle::admit, history.toString());
                        break;
                    case 1:
                        if (operation != null && !operation.registered) {
                            lifecycle.registered(operation.actual);
                            operation.registered = true;
                        }
                        break;
                    case 2:
                        if (operation != null && !operation.resolved) {
                            operation.actual.publish(null, new IllegalStateException("generated operation failure"));
                            operation.resolved = true;
                        }
                        break;
                    case 3:
                        if (operation != null) operation.actual.result.cancel(false);
                        break;
                    case 4: lifecycle.request(true); break;
                    case 5: lifecycle.request(false); break;
                    default: throw new AssertionError(command);
                }
                ShutdownHandle.Snapshot snapshot = lifecycle.snapshot();
                assertTrue(snapshot.phase().ordinal() >= previous.ordinal(), history.toString());
                assertEquals(operations.stream().filter(value -> !value.registered).count(),
                             snapshot.pendingAdmissions(), history.toString());
                assertEquals(operations.stream().filter(value -> !value.registered || !value.resolved).count(),
                             snapshot.outstandingOperations(), history.toString());
                previous = snapshot.phase();
            }
            for (TrackedOperation operation : operations) {
                if (!operation.registered) lifecycle.registered(operation.actual);
                if (!operation.resolved) operation.actual.publish(null, new IllegalStateException("final discharge"));
            }
            lifecycle.request(false);
            lifecycle.finish(null);
            assertEquals(ShutdownHandle.Phase.TERMINATED, lifecycle.snapshot().phase(), history.toString());
            assertEquals(0, lifecycle.snapshot().outstandingOperations(), history.toString());
        }
    }

    private static class TrackedOperation {
        final ShutdownLifecycle.Operation actual;
        boolean registered;
        boolean resolved;
        TrackedOperation(ShutdownLifecycle.Operation actual) { this.actual = actual; }
    }
}
