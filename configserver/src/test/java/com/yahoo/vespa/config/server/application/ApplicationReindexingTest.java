// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.vespa.config.server.application.ApplicationReindexing.Status;
import org.junit.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 */
public class ApplicationReindexingTest {

    @Test
    public void test() {
        ApplicationReindexing reindexing = ApplicationReindexing.empty()
                                                                .withPending("one", "a", 10)
                                                                .withReady("two", "b", Instant.ofEpochMilli(2), 3, "test reindexing")
                                                                .withPending("two", "b", 20)
                                                                .withReady("one", "a", Instant.ofEpochMilli(1), 1, "test reindexing")
                                                                .withReady("two", "a", Instant.ofEpochMilli(3), 2, "test reindexing")
                                                                .withoutPending("one", "a");

        assertEquals(Instant.ofEpochMilli(1),
                     reindexing.status("one", "a").orElseThrow().ready());
        assertEquals(Optional.empty(),
                     reindexing.status("one", "b"));

        assertEquals(Instant.ofEpochMilli(2),
                     reindexing.status("two", "b").orElseThrow().ready());
        assertEquals(Instant.ofEpochMilli(3),
                     reindexing.status("two", "a").orElseThrow().ready());

        assertEquals(Optional.empty(),
                     reindexing.status("three", "a"));

        assertEquals(Optional.empty(),
                     reindexing.lastReadiedAt());
        assertEquals(Optional.of(Instant.ofEpochMilli(3)),
                     reindexing.withoutPending("two", "b").lastReadiedAt());

        // Remove "a" in "one", and "one" entirely.
        assertEquals(Optional.empty(),
                     reindexing.without("one", "a").status("one", "a"));
        assertEquals(Optional.empty(),
                     reindexing.without("one").status("one", "a"));

        // Verify content of "reindexing".
        assertEquals(Set.of("one", "two"),
                     reindexing.clusters().keySet());

        assertEquals(Map.of("a", new Status(Instant.ofEpochMilli(1), 1, "test reindexing")),
                     reindexing.clusters().get("one").ready());

        assertEquals(Map.of(),
                     reindexing.clusters().get("one").pending());

        assertEquals(Map.of("a", new Status(Instant.ofEpochMilli(3), 2, "test reindexing"),
                            "b", new Status(Instant.ofEpochMilli(2), 3, "test reindexing")),
                     reindexing.clusters().get("two").ready());

        assertEquals(Map.of("a", new Status(Instant.ofEpochMilli(3), 2, "test reindexing"),
                            "b", new Status(Instant.ofEpochMilli(2), 0, "test reindexing")),
                     reindexing.withSpeed("two", "b", 0).clusters().get("two").ready());

        assertEquals("no existing reindexing for cluster 'three'",
                     assertThrows(IllegalArgumentException.class, () -> reindexing.withSpeed("three", "a", 4))
                             .getMessage());

        assertEquals("no existing reindexing for document type 'c' in cluster 'two'",
                     assertThrows(IllegalArgumentException.class, () -> reindexing.withSpeed("two", "c", 4))
                             .getMessage());

        assertEquals("Initial reindexing speed must be in (0, 10], but was 0.0",
                     assertThrows(IllegalArgumentException.class, () -> reindexing.withReady("two", "b", Instant.EPOCH, 0, "no"))
                             .getMessage());

        assertEquals("Reindexing speed must be in [0, 10], but was -1.0",
                     assertThrows(IllegalArgumentException.class, () -> reindexing.withSpeed("two", "b", -1))
                             .getMessage());

        assertEquals(Map.of("b", 20L),
                     reindexing.clusters().get("two").pending());

        assertTrue(reindexing.enabled());

        assertFalse(reindexing.enabled(false).enabled());
    }

}
