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
public class ParkedExpirerTest {

    private ProvisioningTester tester;

    @Test
    public void noop_if_not_dynamic_provisioning() {
        tester = getTester(false);
        populateNodeRepo();

        var expirer = new ParkedExpirer(tester.nodeRepository(), Duration.ofMinutes(4), new TestMetric());
        expirer.maintain();

        assertEquals(0, tester.nodeRepository().nodes().list(Node.State.dirty).size());
        assertEquals(25, tester.nodeRepository().nodes().list(Node.State.parked).size());
    }

    @Test
    public void recycles_correct_subset_of_parked_hosts() {
        tester = getTester(true);
        populateNodeRepo();

        var expirer = new ParkedExpirer(tester.nodeRepository(), Duration.ofMinutes(4), new TestMetric());
        expirer.maintain();

        assertEquals(4, tester.nodeRepository().nodes().list(Node.State.dirty).size());
        assertEquals(21, tester.nodeRepository().nodes().list(Node.State.parked).size());

    }

    private ProvisioningTester getTester(boolean dynamicProvisioning) {
        var zone = new Zone(Cloud.builder().dynamicProvisioning(dynamicProvisioning).build(), SystemName.main, Environment.prod, RegionName.from("us-east"));
        return new ProvisioningTester.Builder().zone(zone)
                .hostProvisioner(dynamicProvisioning ? new MockHostProvisioner(List.of()) : null)
                .build();
    }

    private void populateNodeRepo() {
        var nodes = IntStream.range(0, 25)
                             .mapToObj(i -> Node.create("id-" + i, "host-" + i, new Flavor(NodeResources.unspecified()), Node.State.parked, NodeType.host).build())
                             .collect(Collectors.toList());
        tester.nodeRepository().database().addNodesInState(nodes, Node.State.parked, Agent.system);
        tester.nodeRepository().nodes().deprovision(nodes.get(0).hostname(), Agent.system, tester.clock().instant()); // Deprovisioning host is not recycled
    }

}
