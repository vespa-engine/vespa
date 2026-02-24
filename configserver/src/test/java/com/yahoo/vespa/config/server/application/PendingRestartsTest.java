// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author glebashnik
 */
public class PendingRestartsTest {

    @Test
    public void testWithoutPreviousGenerations() {
        // Setup: Create pending restarts with multiple generations
        PendingRestarts restarts = PendingRestarts.empty()
                .withRestarts(0, Set.of("host1", "host2"))
                .withRestarts(1, Set.of("host1", "host3"))
                .withRestarts(2, Set.of("host2", "host4"));

        // Remove host1 and host2 from generations <= 1
        PendingRestarts result = restarts.withoutPreviousGenerations(1, Set.of("host1", "host2"));

        // Verify results
        Map<Long, Set<String>> generations = result.generationsForRestarts();

        // Generation 0 should only have nothing left (both hosts removed)
        assertEquals("Should have 2 generations", 2, generations.size());

        // Generation 1 should only have host3 left
        assertTrue("Generation 1 should exist", generations.containsKey(1L));
        assertEquals("Generation 1 should only have host3", Set.of("host3"), generations.get(1L));

        // Generation 2 should be unchanged (above threshold)
        assertTrue("Generation 2 should exist", generations.containsKey(2L));
        assertEquals("Generation 2 should be unchanged", Set.of("host2", "host4"), generations.get(2L));
    }
}