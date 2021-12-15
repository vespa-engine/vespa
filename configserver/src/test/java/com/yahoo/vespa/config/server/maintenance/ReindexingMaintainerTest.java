// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.maintenance;

import com.yahoo.vespa.config.server.application.ApplicationReindexing;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.yahoo.vespa.config.server.maintenance.ReindexingMaintainer.SPEED;
import static com.yahoo.vespa.config.server.maintenance.ReindexingMaintainer.withNewReady;
import static com.yahoo.vespa.config.server.maintenance.ReindexingMaintainer.withOnlyCurrentData;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class ReindexingMaintainerTest {

    @Test
    public void testReadyComputation() {
        ApplicationReindexing reindexing = ApplicationReindexing.empty()
                                                                .withPending("one", "a", 10)
                                                                .withPending("two", "b", 20).withReady("one", "a", Instant.ofEpochMilli(3), SPEED).withReady("two", "b", Instant.ofEpochMilli(2), SPEED).withReady("two", "c", Instant.ofEpochMilli(3), SPEED);

        // Nothing happens without convergence.
        assertEquals(reindexing,
                     withNewReady(reindexing, () -> -1L, Instant.EPOCH));

        // Status for (one, a) changes, but not (two, b).
        Instant later = Instant.ofEpochMilli(3 << 10);
        // Converged, no longer pending.
        assertEquals(reindexing.withoutPending("one", "a").withReady("one", "a", later, SPEED),   // Converged, now ready.
                     withNewReady(reindexing, () -> 19L, later));

        // Converged, no longer pending.
        // Converged, no Longer pending.
        assertEquals(reindexing.withoutPending("one", "a").withReady("one", "a", later, SPEED)
                               .withoutPending("two", "b").withReady("two", "b", later, SPEED),
                     withNewReady(reindexing, () -> 20L, later));

        // Verify generation supplier isn't called when no pending document types.
        withNewReady(reindexing.withoutPending("one", "a").withoutPending("two", "b"),
                     () -> { throw new AssertionError("not supposed to run"); },
                     later);
    }

    @Test
    public void testGarbageRemoval() {
        ApplicationReindexing reindexing = ApplicationReindexing.empty()
                                                                .withPending("one", "a", 10)
                                                                .withPending("two", "b", 20).withReady("one", "a", Instant.ofEpochMilli(3), SPEED).withReady("two", "b", Instant.ofEpochMilli(2), SPEED).withReady("two", "c", Instant.ofEpochMilli(3), SPEED);

        assertEquals(reindexing,
                     withOnlyCurrentData(reindexing, Map.of("one", List.of("a", "b", "c", "d"),
                                                            "two", List.of("b", "c"),
                                                            "three", List.of("a", "b"))));

        assertEquals(reindexing,
                     withOnlyCurrentData(reindexing, Map.of("one", List.of("a"),
                                                            "two", List.of("b", "c"))));

        assertEquals(ApplicationReindexing.empty()
                                          .withPending("two", "b", 20).withReady("two", "b", Instant.ofEpochMilli(2), SPEED),
                     withOnlyCurrentData(reindexing, Map.of("two", List.of("a", "b"))));

        assertEquals(ApplicationReindexing.empty().withReady("one", "a", Instant.EPOCH, SPEED).without("one", "a").withReady("two", "c", Instant.ofEpochMilli(3), SPEED),
                     withOnlyCurrentData(reindexing, Map.of("one", List.of("c"),
                                                            "two", List.of("c"))));
    }

}
