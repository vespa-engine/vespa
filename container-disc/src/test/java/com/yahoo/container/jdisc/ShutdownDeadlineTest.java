package com.yahoo.container.jdisc;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


import org.junit.jupiter.api.Test;

import static com.yahoo.container.jdisc.ShutdownDeadline.sanitizeFileName;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
class ShutdownDeadlineTest {
    @Test
    void testConfigId2FileName() {
        assertEquals("admin.metrics.2088223-v6-1.ostk.bm2.prod.ne1.yahoo.com", sanitizeFileName("admin/metrics/2088223-v6-1.ostk.bm2.prod.ne1.yahoo.com"));
        assertEquals("admin.standalone.cluster-controllers.1", sanitizeFileName("admin/standalone/cluster-controllers/1 "));
    }
}
