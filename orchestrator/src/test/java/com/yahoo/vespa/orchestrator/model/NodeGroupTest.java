// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.applicationmodel.ApplicationInstance;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.TenantId;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class NodeGroupTest {
    @Test
    public void testBasics() {
        ApplicationInstance applicationInstance = new ApplicationInstance(
                new TenantId("tenant"),
                new ApplicationInstanceId("application-instance"),
                new HashSet<>());

        HostName hostName1 = new HostName("host1");
        HostName hostName2 = new HostName("host2");
        HostName hostName3 = new HostName("host3");
        NodeGroup nodeGroup = new NodeGroup(applicationInstance, hostName1, hostName3);
        nodeGroup.addNode(hostName2);

        // hostnames are sorted (for no good reason other than testability due to stability, readability)
        assertEquals(Arrays.asList(hostName1, hostName2, hostName3), nodeGroup.getHostNames());
        assertEquals("host1,host2,host3", nodeGroup.toCommaSeparatedString());
    }
}
