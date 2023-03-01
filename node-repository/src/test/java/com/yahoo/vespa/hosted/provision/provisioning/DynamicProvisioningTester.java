// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.Nodelike;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaler;
import com.yahoo.vespa.hosted.provision.autoscale.Autoscaling;
import com.yahoo.vespa.hosted.provision.autoscale.Fixture;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.CapacityPolicies;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * A provisioniong tester which
 * - Supports dynamic provisioning (only).
 * - Optionally replicates the actual AWS setup and logic used on Vespa Cloud.
 * - Supports autoscaling testing.
 *
 * TODO: All provisioning testing should migrate to use this, and then the provisionging tester should be collapsed
 *       into this.
 *
 * @author bratseth
 */
public class DynamicProvisioningTester {

    private final ProvisioningTester provisioningTester;
    private final Autoscaler autoscaler;
    private final HostResourcesCalculator hostResourcesCalculator;
    private final CapacityPolicies capacityPolicies;

    public DynamicProvisioningTester(Zone zone, HostResourcesCalculator resourcesCalculator, List<Flavor> hostFlavors, InMemoryFlagSource flagSource, int hostCount) {
        this(zone, hostFlavors, resourcesCalculator, flagSource);
        for (Flavor flavor : hostFlavors)
            provisioningTester.makeReadyNodes(hostCount, flavor.name(), NodeType.host, 8);
        provisioningTester.activateTenantHosts();
    }

    private DynamicProvisioningTester(Zone zone, List<Flavor> flavors, HostResourcesCalculator resourcesCalculator, InMemoryFlagSource flagSource) {
        MockHostProvisioner hostProvisioner = null;
        if (zone.cloud().dynamicProvisioning()) {
            hostProvisioner = new MockHostProvisioner(flavors);
            hostProvisioner.setHostFlavorIfAvailable(new NodeResources(2, 8, 75, 10, NodeResources.DiskSpeed.fast, NodeResources.StorageType.remote), resourcesCalculator, ClusterSpec.Type.admin
            );
        }

        provisioningTester = new ProvisioningTester.Builder().zone(zone)
                                                             .flavors(flavors)
                                                             .resourcesCalculator(resourcesCalculator)
                                                             .flagSource(flagSource)
                                                             .hostProvisioner(hostProvisioner)
                                                             .build();

        hostResourcesCalculator = resourcesCalculator;
        autoscaler = new Autoscaler(nodeRepository());
        capacityPolicies = new CapacityPolicies(provisioningTester.nodeRepository());
    }

    private static List<Flavor> toFlavors(List<NodeResources> resources) {
        List<Flavor> flavors = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++)
            flavors.add(new Flavor("flavor" + i, resources.get(i)));
        return flavors;
    }

    public static Fixture.Builder fixture() { return new Fixture.Builder(); }

    public static Fixture.Builder fixture(ClusterResources min, ClusterResources now, ClusterResources max) {
        return new Fixture.Builder().initialResources(Optional.of(now)).capacity(Capacity.from(min, max));
    }

    public ProvisioningTester provisioning() { return provisioningTester; }

    public static ApplicationId applicationId(String applicationName) {
        return ApplicationId.from("tenant1", applicationName, "instance1");
    }

    public static ClusterSpec clusterSpec(ClusterSpec.Type type, String clusterId) {
        return ClusterSpec.request(type, ClusterSpec.Id.from(clusterId)).vespaVersion("7").build();
    }

    public void deploy(ApplicationId application, ClusterSpec cluster, ClusterResources resources) {
        deploy(application, cluster, resources.nodes(), resources.groups(), resources.nodeResources());
    }

    public List<HostSpec> deploy(ApplicationId application, ClusterSpec cluster, int nodes, int groups, NodeResources resources) {
        return deploy(application, cluster, Capacity.from(new ClusterResources(nodes, groups, resources)));
    }

    public List<HostSpec> deploy(ApplicationId application, ClusterSpec cluster, Capacity capacity) {
        List<HostSpec> hosts = provisioningTester.prepare(application, cluster, capacity);
        for (HostSpec host : hosts)
            makeReady(host.hostname());
        provisioningTester.activateTenantHosts();
        provisioningTester.activate(application, hosts);
        return hosts;
    }

    public void makeReady(String hostname) {
        Node node = nodeRepository().nodes().node(hostname).get();
        provisioningTester.patchNode(node, (n) -> n.with(new IP.Config(Set.of("::" + 0 + ":0"), Set.of())));
        Node host = nodeRepository().nodes().node(node.parentHostname().get()).get();
        host = host.with(new IP.Config(Set.of("::" + 0 + ":0"), Set.of("::" + 0 + ":2")));
        if (host.state() == Node.State.provisioned)
            provisioningTester.move(Node.State.ready, host);
    }

    public void deactivateRetired(ApplicationId application, ClusterSpec cluster, Capacity capacity) {
        try (Mutex lock = nodeRepository().applications().lock(application)) {
            for (Node node : nodeRepository().nodes().list(Node.State.active).owner(application)) {
                if (node.allocation().get().membership().retired())
                    nodeRepository().nodes().write(node.with(node.allocation().get().removable(true, true)), lock);
            }
        }
        deploy(application, cluster, capacity);
    }

    /** Creates a single redeployment event with bogus data except for the given duration */
    public void setScalingDuration(ApplicationId applicationId, ClusterSpec.Id clusterId, Duration duration) {
        Application application = nodeRepository().applications().require(applicationId);
        Cluster cluster = application.cluster(clusterId).get();
        cluster = new Cluster(clusterId,
                              cluster.exclusive(),
                              cluster.minResources(),
                              cluster.maxResources(),
                              cluster.groupSize(),
                              cluster.required(),
                              cluster.suggested(),
                              cluster.target(),
                              cluster.clusterInfo(),
                              cluster.bcpGroupInfo(),
                              List.of()); // Remove scaling events
        cluster = cluster.with(ScalingEvent.create(cluster.minResources(), cluster.minResources(),
                                                   0,
                                                   clock().instant().minus(Duration.ofDays(1).minus(duration))).withCompletion(clock().instant().minus(Duration.ofDays(1))));
        application = application.with(cluster);
        nodeRepository().applications().put(application, nodeRepository().applications().lock(applicationId));
    }

    public Autoscaling autoscale(ApplicationId applicationId, ClusterSpec cluster, Capacity capacity) {
        capacity = capacityPolicies.applyOn(capacity, applicationId, capacityPolicies.decideExclusivity(capacity, cluster).isExclusive());
        Application application = nodeRepository().applications().get(applicationId).orElse(Application.empty(applicationId))
                                                  .withCluster(cluster.id(), false, capacity);
        try (Mutex lock = nodeRepository().applications().lock(applicationId)) {
            nodeRepository().applications().put(application, lock);
        }
        return autoscaler.autoscale(application, application.clusters().get(cluster.id()),
                                    nodeRepository().nodes().list(Node.State.active).owner(applicationId));
    }

    public Autoscaling suggest(ApplicationId applicationId, ClusterSpec.Id clusterId,
                                     ClusterResources min, ClusterResources max) {
        Application application = nodeRepository().applications().get(applicationId).orElse(Application.empty(applicationId))
                                                  .withCluster(clusterId, false, Capacity.from(min, max));
        try (Mutex lock = nodeRepository().applications().lock(applicationId)) {
            nodeRepository().applications().put(application, lock);
        }
        return autoscaler.suggest(application, application.clusters().get(clusterId),
                                  nodeRepository().nodes().list(Node.State.active).owner(applicationId));
    }

    public void assertResources(String message,
                                int nodeCount, int groupCount,
                                NodeResources expectedResources,
                                ClusterResources resources) {
        assertResources(message, nodeCount, groupCount,
                        expectedResources.vcpu(), expectedResources.memoryGb(), expectedResources.diskGb(),
                        resources);
    }

    public ClusterResources assertResources(String message,
                                            int nodeCount, int groupCount,
                                            double approxCpu, double approxMemory, double approxDisk,
                                            Autoscaling autoscaling) {
        assertTrue("Resources are present: " + message + " (" + autoscaling + ": " + autoscaling.status() + ")",
                   autoscaling.resources().isPresent());
        var resources = autoscaling.resources().get();
        assertResources(message, nodeCount, groupCount, approxCpu, approxMemory, approxDisk, resources);
        return resources;
    }

    public void assertResources(String message,
                                int nodeCount, int groupCount,
                                double approxCpu, double approxMemory, double approxDisk,
                                ClusterResources resources) {
        double delta = 0.0000000001;
        NodeResources nodeResources = resources.nodeResources();
        assertEquals("Node count in " + resources + ": " + message, nodeCount, resources.nodes());
        assertEquals("Group count in " + resources+ ": " + message, groupCount, resources.groups());
        assertEquals("Cpu in " + resources + ": " + message, approxCpu, Math.round(nodeResources.vcpu() * 10) / 10.0, delta);
        assertEquals("Memory in " + resources + ": " + message, approxMemory, Math.round(nodeResources.memoryGb() * 10) / 10.0, delta);
        assertEquals("Disk in: " + resources + ": "  + message, approxDisk, Math.round(nodeResources.diskGb() * 10) / 10.0, delta);
    }

    public ManualClock clock() {
        return provisioningTester.clock();
    }

    public NodeRepository nodeRepository() {
        return provisioningTester.nodeRepository();
    }

    public MetricsDb nodeMetricsDb() { return nodeRepository().metricsDb(); }

    // TODO: Discontinue use of this
    public static class MockHostResourcesCalculator implements HostResourcesCalculator {

        private final Zone zone;

        public MockHostResourcesCalculator(Zone zone) {
            this.zone = zone;
        }

        @Override
        public NodeResources realResourcesOf(Nodelike node, NodeRepository nodeRepository) {
            if (zone.cloud().dynamicProvisioning())
                return node.resources().withMemoryGb(node.resources().memoryGb());
            else
                return node.resources();
        }

        @Override
        public NodeResources advertisedResourcesOf(Flavor flavor) {
            if (zone.cloud().dynamicProvisioning())
                return flavor.resources().withMemoryGb(flavor.resources().memoryGb());
            else
                return flavor.resources();
        }

        @Override
        public NodeResources requestToReal(NodeResources resources, boolean exclusive, boolean bestCase) {
            return resources.withMemoryGb(resources.memoryGb());
        }

        @Override
        public NodeResources realToRequest(NodeResources resources, boolean exclusive, boolean bestCase) {
            return resources.withMemoryGb(resources.memoryGb());
        }

        @Override
        public long reservedDiskSpaceInBase2Gb(NodeType nodeType, boolean sharedHost) { return 0; }

    }

    private class MockHostProvisioner extends com.yahoo.vespa.hosted.provision.testutils.MockHostProvisioner {

        public MockHostProvisioner(List<Flavor> flavors) {
            super(flavors);
        }

        @Override
        public boolean compatible(Flavor flavor, NodeResources resources) {
            NodeResources flavorResources = hostResourcesCalculator.advertisedResourcesOf(flavor);
            if (flavorResources.storageType() == NodeResources.StorageType.remote
                && resources.diskGb() <= flavorResources.diskGb())
                flavorResources = flavorResources.withDiskGb(resources.diskGb());

            if (flavorResources.bandwidthGbps() >= resources.bandwidthGbps())
                flavorResources = flavorResources.withBandwidthGbps(resources.bandwidthGbps());

            return flavorResources.compatibleWith(resources);
        }

    }

}
