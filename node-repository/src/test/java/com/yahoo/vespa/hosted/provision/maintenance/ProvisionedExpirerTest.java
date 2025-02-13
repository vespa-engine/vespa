// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import org.junit.Test;

import java.time.Duration;
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
        InMemoryFlagSource flagSource = new InMemoryFlagSource();
        flagSource = flagSource.withIntFlag(PermanentFlags.KEEP_PROVISIONED_EXPIRED_HOSTS_MAX.id(), 5);

        populateNodeRepo();

        tester.clock().advance(Duration.ofMinutes(5));
        new ProvisionedExpirer(tester.nodeRepository(), Duration.ofMinutes(4), flagSource, new TestMetric()).maintain();

        assertEquals(10, tester.nodeRepository().nodes().list().deprovisioning().size());
        assertEquals(5, tester.nodeRepository().nodes().list().not().deprovisioning().size());
    }

    private void populateNodeRepo() {
        var nodes = IntStream.range(0, 15)
                             .mapToObj(i -> Node.create("id-" + i, "host-" + i, new Flavor(NodeResources.unspecified()), Node.State.provisioned, NodeType.host).build())
                             .toList();
        tester.nodeRepository().database().addNodesInState(new LockedNodeList(nodes, () -> { }), Node.State.provisioned, Agent.system);
    }

}
