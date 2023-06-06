// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.HostEvent;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;
import com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner;
import org.junit.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class HostRetirerTest {

    @Test
    public void retire_hosts() {
        NodeFlavors flavors = FlavorConfigBuilder.createDummies("default");
        MockHostProvisioner hostProvisioner = new MockHostProvisioner(flavors.getFlavors());
        ProvisioningTester tester = new ProvisioningTester.Builder().hostProvisioner(hostProvisioner)
                                                                    .flavors(flavors.getFlavors())
                                                                    .dynamicProvisioning()
                                                                    .build();
        HostRetirer retirer = new HostRetirer(tester.nodeRepository(), Duration.ofDays(1), new MockMetric(), hostProvisioner);
        tester.makeReadyHosts(3, new NodeResources(24, 48, 1000, 10))
              .activateTenantHosts();
        List<String> hostIds = tester.nodeRepository().nodes().list(Node.State.active).mapToList(Node::id);

        // No events scheduled
        retirer.maintain();
        NodeList hosts = tester.nodeRepository().nodes().list();
        assertEquals(0, hosts.deprovisioning().size());

        // Event is scheduled for one known host
        hostProvisioner.addEvent(new HostEvent("event0", hostIds.get(1), getClass().getSimpleName()))
                       .addEvent(new HostEvent("event1", "unknown-host-id", getClass().getSimpleName()));

        // Next run retires host
        retirer.maintain();
        hosts = tester.nodeRepository().nodes().list();
        assertEquals(1, hosts.deprovisioning().size());
    }

}
