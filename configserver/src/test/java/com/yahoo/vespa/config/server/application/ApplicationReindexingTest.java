// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.vespa.config.server.application.ApplicationReindexing.Status;
import org.junit.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class ApplicationReindexingTest {

    @Test
    public void test() {
        ApplicationReindexing reindexing = ApplicationReindexing.ready(Instant.EPOCH)
                                                                .withReady(Instant.ofEpochMilli(1 << 20))
                                                                .withPending("one", "a", 10)
                                                                .withReady("two", "b", Instant.ofEpochMilli(2))
                                                                .withPending("two", "b", 20)
                                                                .withReady("two", Instant.ofEpochMilli(2 << 10))
                                                                .withReady("one", "a", Instant.ofEpochMilli(1))
                                                                .withReady("two", "c", Instant.ofEpochMilli(3));

        // Document is most specific, and is used.
        assertEquals(Instant.ofEpochMilli(1),
                     reindexing.status("one", "a").orElseThrow().ready());

        // Cluster is most specific, and inherits application's common status.
        assertEquals(Instant.ofEpochMilli(1 << 20),
                     reindexing.status("one", "d").orElseThrow().ready());

        // Cluster is most specific, and has its own status set.
        assertEquals(Instant.ofEpochMilli(2 << 10),
                     reindexing.status("two", "d").orElseThrow().ready());

        // Application is most specific, as cluster and documeent are unknown.
        assertEquals(Instant.ofEpochMilli(1 << 20),
                     reindexing.status("three", "a").orElseThrow().ready());

        assertEquals(Optional.empty(),
                     reindexing.status("two", "b"));

        assertEquals(new Status(Instant.ofEpochMilli(1 << 20)),
                     reindexing.common());

        assertEquals(Set.of("one", "two"),
                     reindexing.clusters().keySet());

        assertEquals(new Status(Instant.ofEpochMilli(1 << 20)),
                     reindexing.clusters().get("one").common());

        assertEquals(Map.of("a", new Status(Instant.ofEpochMilli(1))),
                     reindexing.clusters().get("one").ready());

        assertEquals(Map.of(),
                     reindexing.clusters().get("one").pending());

        assertEquals(new Status(Instant.ofEpochMilli(2 << 10)),
                     reindexing.clusters().get("two").common());

        assertEquals(Map.of("c", new Status(Instant.ofEpochMilli(3))),
                     reindexing.clusters().get("two").ready());

        assertEquals(Map.of("b", 20L),
                     reindexing.clusters().get("two").pending());

        reindexing = reindexing.enabled(false);

        // When disabled, status of any document type is empty.
        assertEquals(Optional.empty(),
                     reindexing.status("one", "a"));

        assertEquals(Optional.empty(),
                     reindexing.status("one", "d"));

        assertEquals(Optional.empty(),
                     reindexing.status("two", "d"));

        assertEquals(Optional.empty(),
                     reindexing.status("three", "a"));

        assertEquals(Optional.empty(),
                     reindexing.status("two", "b"));

    }

}
