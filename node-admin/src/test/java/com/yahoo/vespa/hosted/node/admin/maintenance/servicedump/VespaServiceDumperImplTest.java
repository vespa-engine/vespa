package com.yahoo.vespa.hosted.node.admin.maintenance.servicedump;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
class VespaServiceDumperImplTest {
    @Test
    void creates_valid_dump_id_from_dump_request() {
        long nowMillis = Instant.now().toEpochMilli();
        ServiceDumpReport request = new ServiceDumpReport(
                nowMillis, null, null, null, null, "default/container.3", null, null);
        String dumpId = VespaServiceDumperImpl.createDumpId(request);
        assertEquals("default-container-3-" + nowMillis, dumpId);
    }
}