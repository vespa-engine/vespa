// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import org.junit.Test;

import java.time.Instant;

import static com.yahoo.vespa.config.server.maintenance.ReindexingMaintainer.withReady;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class ReindexingMaintainerTest {

    @Test
    public void testReadyComputation() {
        ApplicationReindexing reindexing = ApplicationReindexing.ready(Instant.ofEpochMilli(1 << 20))
                                                                .withPending("one", "a", 10)
                                                                .withReady("two", "b", Instant.ofEpochMilli(2))
                                                                .withPending("two", "b", 20)
                                                                .withReady("two", Instant.ofEpochMilli(2 << 10))
                                                                .withReady("one", "a", Instant.ofEpochMilli(1))
                                                                .withReady("two", "c", Instant.ofEpochMilli(3));

        assertEquals(reindexing,
                     withReady(reindexing, () -> -1L, Instant.EPOCH));

        assertEquals(reindexing,
                     withReady(reindexing, () -> 19L, Instant.EPOCH));

        Instant later = Instant.ofEpochMilli(2).plus(ReindexingMaintainer.reindexingInterval);
        assertEquals(reindexing.withReady("one", later)         // Had EPOCH as previous, so is updated.
                               .withReady("two", "b", later)    // Got config convergence, so is updated.
                               .withReady("one", "a", later),   // Had EPOCH + 1 as previous, so is updated.
                     withReady(reindexing, () -> 20L, later));

        // Verify generation supplier isn't called when no pending document types.
        withReady(reindexing.withReady("two", "b", later), () -> { throw new AssertionError("not supposed to run"); }, later);
    }

}
