// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.status;

import com.yahoo.jdisc.test.TestTimer;
import com.yahoo.vespa.orchestrator.status.json.WireHostInfo;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * @author hakonhall
 */
public class HostInfoTest {
    private final TestTimer timer = new TestTimer();

    @Before
    public void setUp() {
        timer.setMillis(3L);
    }

    @Test
    public void equality() {
        HostInfo info1 = HostInfo.createSuspended(HostStatus.ALLOWED_TO_BE_DOWN, timer.currentTime());
        assertNotEquals(info1, HostInfo.createNoRemarks());
        assertNotEquals(info1, HostInfo.createSuspended(HostStatus.PERMANENTLY_DOWN, timer.currentTime()));

        byte[] serialized = WireHostInfo.serialize(info1);
        HostInfo deserialized1 = WireHostInfo.deserialize(serialized);
        assertEquals(info1, deserialized1);
    }
}