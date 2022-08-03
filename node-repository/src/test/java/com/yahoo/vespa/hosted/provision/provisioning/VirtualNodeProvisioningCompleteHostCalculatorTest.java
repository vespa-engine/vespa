// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.Nodelike;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class VirtualNodeProvisioningCompleteHostCalculatorTest {

    @Test
    public void changing_to_different_range_preserves_allocation() {
        Flavor hostFlavor = new Flavor(new NodeResources(40, 40, 1000, 4));
        ProvisioningTester tester = new ProvisioningTester.Builder().zone(new Zone(Environment.prod, RegionName.from("us-east")))
                                                                    .resourcesCalculator(new CompleteResourcesCalculator(hostFlavor))
                                                                    .flavors(List.of(hostFlavor))
                                                                    .build();
        tester.makeReadyHosts(9, hostFlavor.resources()).activateTenantHosts();

        ApplicationId app1 = ProvisioningTester.applicationId("app1");
        ClusterSpec cluster1 = ClusterSpec.request(ClusterSpec.Type.content, new ClusterSpec.Id("cluster1")).vespaVersion("7").build();

        var initialResources = new NodeResources(20, 16, 50, 1);
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(2, 1, initialResources)));
        tester.assertNodes("Initial allocation",
                           2, 1, 20, 16, 50, 1.0,
                           app1, cluster1);

        var newMinResources = new NodeResources( 5,  4, 11, 1);
        var newMaxResources = new NodeResources(20, 10, 30, 1);

        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(7, 1, newMinResources),
                                                      new ClusterResources(7, 1, newMaxResources)));
        tester.assertNodes("New allocation preserves (redundancy adjusted) total resources",
                           7, 1, 5, 4.0, 11, 1.0,
                           app1, cluster1);
        tester.activate(app1, cluster1, Capacity.from(new ClusterResources(7, 1, newMinResources),
                                                      new ClusterResources(7, 1, newMaxResources)));
        tester.assertNodes("Redeploying the same ranges does not cause changes",
                           7, 1, 5, 4.0, 11, 1.0,
                           app1, cluster1);
    }

    @Test
    public void testResourcesCalculator() {
        Flavor hostFlavor = new Flavor(new NodeResources(20, 40, 1000, 4));
        var calculator = new CompleteResourcesCalculator(hostFlavor);
        var originalReal = new NodeResources(0.7, 6.0, 12.9, 1.0);
        var realToRequest = calculator.realToRequest(originalReal, false);
        var requestToReal = calculator.requestToReal(realToRequest, false);
        var realResourcesOf = calculator.realResourcesOf(realToRequest);
        assertEquals(originalReal, requestToReal);
        assertEquals(originalReal, realResourcesOf);
    }

    private static class CompleteResourcesCalculator implements HostResourcesCalculator {

        private final Flavor hostFlavor; // Has the real resources
        private final double memoryOverhead = 1;
        private final double diskOverhead = 100;

        public CompleteResourcesCalculator(Flavor hostFlavor) {
            this.hostFlavor = hostFlavor;
        }

        @Override
        public NodeResources realResourcesOf(Nodelike node, NodeRepository nodeRepository) {
            if (node.parentHostname().isEmpty()) return node.resources(); // hosts use configured flavors
            return realResourcesOf(node.resources());
        }

        NodeResources realResourcesOf(NodeResources advertisedResources) {
            return advertisedResources.withMemoryGb(advertisedResources.memoryGb() -
                                                    memoryOverhead(advertisedResourcesOf(hostFlavor).memoryGb(), advertisedResources, false))
                                      .withDiskGb(advertisedResources.diskGb() -
                                                  diskOverhead(advertisedResourcesOf(hostFlavor).diskGb(), advertisedResources, false));
        }

        @Override
        public NodeResources requestToReal(NodeResources advertisedResources, boolean exclusive) {
            double memoryOverhead = memoryOverhead(advertisedResourcesOf(hostFlavor).memoryGb(), advertisedResources, false);
            double diskOverhead = diskOverhead(advertisedResourcesOf(hostFlavor).diskGb(), advertisedResources, false);
            return advertisedResources.withMemoryGb(advertisedResources.memoryGb() - memoryOverhead)
                                      .withDiskGb(advertisedResources.diskGb() - diskOverhead);
        }

        @Override
        public NodeResources advertisedResourcesOf(Flavor flavor) {
            if ( ! flavor.equals(hostFlavor)) return flavor.resources(); // Node 'flavors' just wrap the advertised resources
            return hostFlavor.resources().withMemoryGb(hostFlavor.resources().memoryGb() + memoryOverhead)
                             .withDiskGb(hostFlavor.resources().diskGb() + diskOverhead);
        }

        @Override
        public NodeResources realToRequest(NodeResources realResources, boolean exclusive) {
            double memoryOverhead = memoryOverhead(advertisedResourcesOf(hostFlavor).memoryGb(), realResources, true);
            double diskOverhead = diskOverhead(advertisedResourcesOf(hostFlavor).diskGb(), realResources, true);
            return realResources.withMemoryGb(realResources.memoryGb() + memoryOverhead)
                                .withDiskGb(realResources.diskGb() + diskOverhead);
        }

        @Override
        public long reservedDiskSpaceInBase2Gb(NodeType nodeType, boolean sharedHost) { return 0; }

        /**
         * Returns the memory overhead resulting if the given advertised resources are placed on the given node
         *
         * @param real true if the given resources are in real values, false if they are in advertised
         */
        private double memoryOverhead(double hostAdvertisedMemoryGb, NodeResources resources, boolean real) {
            double memoryShare = resources.memoryGb() /
                                 ( hostAdvertisedMemoryGb - (real ? memoryOverhead : 0));
            return memoryOverhead * memoryShare;
        }

        /**
         * Returns the disk overhead resulting if the given advertised resources are placed on the given node
         *
         * @param real true if the resources are in real values, false if they are in advertised
         */
        private double diskOverhead(double hostAdvertisedDiskGb, NodeResources resources, boolean real) {
            double diskShare = resources.diskGb() /
                               ( hostAdvertisedDiskGb - (real ? diskOverhead : 0) );
            return diskOverhead * diskShare;
        }

    }

}
