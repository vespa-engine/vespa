// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * @author olaa
 */
public class ProvisionedExpirerTest {

    private ProvisioningTester tester;

    @Test
    public void deprovisions_hosts_if_excessive_expiry() {
        tester = new ProvisioningTester.Builder().build();
        populateNodeRepo();

        tester.clock().advance(Duration.ofMinutes(5));
        new ProvisionedExpirer(tester.nodeRepository(), Duration.ofMinutes(4), new TestMetric()).maintain();

        assertEquals(5, tester.nodeRepository().nodes().list().deprovisioning().size());
        assertEquals(20, tester.nodeRepository().nodes().list().not().deprovisioning().size());
    }

    private void populateNodeRepo() {
        var nodes = IntStream.range(0, 25)
                             .mapToObj(i -> Node.create("id-" + i, "host-" + i, new Flavor(NodeResources.unspecified()), Node.State.provisioned, NodeType.host).build())
                             .collect(Collectors.toList());
        tester.nodeRepository().database().addNodesInState(nodes, Node.State.provisioned, Agent.system);
    }

}
