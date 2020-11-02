// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import org.junit.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class ReindexingStatusTest {

    @Test
    public void test() {
        ReindexingStatus status = ReindexingStatus.empty()
                                                  .withPending("one", 1)
                                                  .withPending("two", 2)
                                                  .withReady("two", Instant.EPOCH)
                                                  .withPending("three", 2)
                                                  .withReady("three", Instant.EPOCH)
                                                  .withPending("three", 3)
                                                  .withReady("four", Instant.MIN)
                                                  .withReady("four", Instant.MAX);
        assertEquals(Map.of("one", 1L,
                            "three", 3L), status.pending());
        assertEquals(Map.of("two", new ReindexingStatus.Status(Instant.EPOCH),
                            "four", new ReindexingStatus.Status(Instant.MAX)),
                     status.status());
    }

}
