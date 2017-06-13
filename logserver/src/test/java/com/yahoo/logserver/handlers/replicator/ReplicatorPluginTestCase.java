// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.replicator;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.plugin.SystemPropertyConfig;

public class ReplicatorPluginTestCase {

    ReplicatorPlugin rp;

    @Before
    public void setUp() {
        rp = new ReplicatorPlugin();
    }

    @After
    public void tearDown() {
        if (rp != null) {
            rp.shutdownPlugin();
        }
    }

    @Test
    public void testReplicatorPlugin() {
        System.setProperty("replicatorplugin.test.port", "18325");
        try {
            rp.shutdownPlugin();
            fail("Shutdown before init didn't throw.");
        } catch (Exception e) {
        }
        rp.initPlugin(new SystemPropertyConfig("replicatorplugin.test."));
        try {
            rp.initPlugin(new SystemPropertyConfig("test"));
            fail("Multiple init didn't throw.");
        } catch (Exception e) {
        }
    }
}
