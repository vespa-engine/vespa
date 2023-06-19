package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author mpolden
 */
class DeprovisionedExpirerTest {

    private final NodeFlavors flavors = FlavorConfigBuilder.createDummies("host");
    private final ProvisioningTester tester = new ProvisioningTester.Builder().dynamicProvisioning()
                                                                .flavors(flavors.getFlavors())
                                                                .hostProvisioner(new MockHostProvisioner(flavors.getFlavors()))
                                                                .build();
    private final DeprovisionedExpirer expirer = new DeprovisionedExpirer(tester.nodeRepository(), Duration.ofDays(30),
                                                                          new TestMetric());

    @Test
    public void maintain() {
        tester.makeReadyHosts(1, new NodeResources(2,4,8,1))
              .activateTenantHosts();
        NodeList hosts = tester.nodeRepository().nodes().list().state(Node.State.active);
        assertEquals(1, hosts.size());

        // Remove host
        String hostname = hosts.first().get().hostname();
        tester.nodeRepository().nodes().park(hostname, false, Agent.system, getClass().getSimpleName());
        tester.nodeRepository().nodes().removeRecursively(hostname);
        assertSame(Node.State.deprovisioned, tester.node(hostname).state());

        // Host is not removed until expiry passes
        assertExpiredAfter(Duration.ZERO, false, hostname);
        assertExpiredAfter(Duration.ofDays(15), false, hostname);
        assertExpiredAfter(Duration.ofDays(15), true, hostname);
    }

    private void assertExpiredAfter(Duration duration, boolean expired, String hostname) {
        tester.clock().advance(duration);
        expirer.maintain();
        assertEquals(expired, tester.nodeRepository().nodes().node(hostname).isEmpty());
    }

}
