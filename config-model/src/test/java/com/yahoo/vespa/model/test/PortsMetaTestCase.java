// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.yahoo.vespa.model.PortsMeta;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests proper functioning of the PortsMeta.
 *
 * @author Vidar Larsen
 */
public class PortsMetaTestCase {

    @Test
    public void testRegister() throws Exception {
        PortsMeta pm = new PortsMeta();
        pm.on(0).tag("foo");
        pm.on(1).tag("bar");
        pm.on(5).tag("xyzzy");

        assertTrue(pm.contains(0, "foo"));
        assertTrue(pm.contains(1, "bar"));
        assertTrue(pm.contains(5, "xyzzy"));
        assertFalse(pm.contains(0, "bar"));
        assertFalse(pm.contains(2, "anything"));
    }

    @Test
    public void testAdminStatusApi() throws Exception {
        PortsMeta pm = new PortsMeta()
                .on(0).tag("rpc").tag("nc").tag("admin").tag("status")
                .on(1).tag("rpc").tag("rtx").tag("admin").tag("status")
                .on(2).tag("http").tag("admin");

        assertEquals(1, pm.getRpcAdminOffset().intValue());
        assertEquals(1, pm.getRpcStatusOffset().intValue());
        assertEquals(2, pm.getHttpAdminOffset().intValue());
        assertNull(pm.getHttpStatusOffset());
    }

}
