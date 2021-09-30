// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.vespa.config.server.application.ApplicationReindexing.Status;
import org.junit.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jonmv
 */
public class ApplicationReindexingTest {

    @Test
    public void test() {
        ApplicationReindexing reindexing = ApplicationReindexing.empty()
                                                                .withPending("one", "a", 10)
                                                                .withReady("two", "b", Instant.ofEpochMilli(2))
                                                                .withPending("two", "b", 20)
                                                                .withReady("one", "a", Instant.ofEpochMilli(1))
                                                                .withReady("two", "a", Instant.ofEpochMilli(3))
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

        // Remove "a" in "one", and "one" entirely.
        assertEquals(Optional.empty(),
                     reindexing.without("one", "a").status("one", "a"));
        assertEquals(Optional.empty(),
                     reindexing.without("one").status("one", "a"));

        // Verify content of "reindexing".
        assertEquals(Set.of("one", "two"),
                     reindexing.clusters().keySet());

        assertEquals(Map.of("a", new Status(Instant.ofEpochMilli(1))),
                     reindexing.clusters().get("one").ready());

        assertEquals(Map.of(),
                     reindexing.clusters().get("one").pending());

        assertEquals(Map.of("a", new Status(Instant.ofEpochMilli(3)),
                            "b", new Status(Instant.ofEpochMilli(2))),
                     reindexing.clusters().get("two").ready());

        assertEquals(Map.of("b", 20L),
                     reindexing.clusters().get("two").pending());

        assertTrue(reindexing.enabled());

        assertFalse(reindexing.enabled(false).enabled());
    }

}
