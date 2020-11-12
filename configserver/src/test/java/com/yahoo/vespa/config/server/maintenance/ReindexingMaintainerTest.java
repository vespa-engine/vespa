// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import org.junit.Test;

import java.time.Instant;

import static com.yahoo.vespa.config.server.maintenance.ReindexingMaintainer.withConvergenceOn;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class ReindexingMaintainerTest {

    @Test
    public void testReadyComputation() {
        ApplicationReindexing reindexing = ApplicationReindexing.ready(Instant.ofEpochMilli(1 << 20))
                                                                .withPending("one", "a", 10)
                                                                .withPending("two", "b", 20)
                                                                .withReady("one", Instant.EPOCH)
                                                                .withReady("one", "a", Instant.ofEpochMilli(1))
                                                                .withReady("two", Instant.ofEpochMilli(2 << 10))
                                                                .withReady("two", "b", Instant.ofEpochMilli(2))
                                                                .withReady("two", "c", Instant.ofEpochMilli(3));

        // Nothing happens without convergence.
        assertEquals(reindexing,
                     withConvergenceOn(reindexing, () -> -1L, Instant.EPOCH));

        // Status for (one, a) changes, but not (two, b).

        assertEquals(reindexing.withReady("one", "a", Instant.EPOCH).withoutPending("one", "a"),
                     withConvergenceOn(reindexing, () -> 19L, Instant.EPOCH));

        Instant later = Instant.ofEpochMilli(2).plus(ReindexingMaintainer.reindexingInterval);
        assertEquals(reindexing.withReady("one", later)         // Had EPOCH as previous, so is updated, overwriting status for "a".
                               .withReady("two", "b", later)    // Got config convergence, so is updated.
                               .withoutPending("one", "a")
                               .withoutPending("two", "b"),
                     withConvergenceOn(reindexing, () -> 20L, later));

        // Verify generation supplier isn't called when no pending document types.
        withConvergenceOn(reindexing.withoutPending("one", "a").withoutPending("two", "b"),
                          () -> { throw new AssertionError("not supposed to run"); },
                          later);
    }

}
