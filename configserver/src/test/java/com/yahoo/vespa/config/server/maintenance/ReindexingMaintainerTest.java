// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import org.junit.Test;

import java.time.Instant;

import static com.yahoo.vespa.config.server.maintenance.ReindexingMaintainer.withNewReady;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class ReindexingMaintainerTest {

    @Test
    public void testReadyComputation() {
        ApplicationReindexing reindexing = ApplicationReindexing.ready(Instant.EPOCH)
                                                                .withPending("one", "a", 10)
                                                                .withPending("two", "b", 20)
                                                                .withReady("one", Instant.ofEpochMilli(2))
                                                                .withReady("one", "a", Instant.ofEpochMilli(3))
                                                                .withReady("two", Instant.ofEpochMilli(2 << 10))
                                                                .withReady("two", "b", Instant.ofEpochMilli(2))
                                                                .withReady("two", "c", Instant.ofEpochMilli(3));

        // Nothing happens without convergence.
        assertEquals(reindexing,
                     withNewReady(reindexing, () -> -1L, Instant.EPOCH));

        // Status for (one, a) changes, but not (two, b).
        Instant later = Instant.ofEpochMilli(3 << 10);
        assertEquals(reindexing.withoutPending("one", "a")      // Converged, no longer pending.
                               .withReady("one", "a", later),   // Converged, now ready.
                     withNewReady(reindexing, () -> 19L, later));

        assertEquals(reindexing.withoutPending("one", "a")      // Converged, no longer pending.
                               .withoutPending("two", "b")      // Converged, no Longer pending.
                               .withReady(later),               // Outsider calls withReady(later), overriding more specific status.
                     withNewReady(reindexing, () -> 20L, later).withReady(later));

        // Verify generation supplier isn't called when no pending document types.
        withNewReady(reindexing.withoutPending("one", "a").withoutPending("two", "b"),
                     () -> { throw new AssertionError("not supposed to run"); },
                     later);
    }

}
