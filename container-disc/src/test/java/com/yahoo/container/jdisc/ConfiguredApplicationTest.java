// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfiguredApplicationTest {
    @Test
    public void testConfigId2FileName() {
        assertEquals("admin.metrics.2088223-v6-1.ostk.bm2.prod.ne1.yahoo.com", ConfiguredApplication.santizeFileName("admin/metrics/2088223-v6-1.ostk.bm2.prod.ne1.yahoo.com"));
        assertEquals("admin.standalone.cluster-controllers.1", ConfiguredApplication.santizeFileName("admin/standalone/cluster-controllers/1 "));
    }
}
