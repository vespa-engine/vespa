// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.Nodelike;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
class AutoscalingTester {

    private final ProvisioningTester provisioningTester;
    private final Autoscaler autoscaler;
    private final MemoryMetricsDb db;
    private final MockHostResourcesCalculator hostResourcesCalculator;

    /** Creates an autoscaling tester with a single host type ready */
    public AutoscalingTester(NodeResources hostResources) {
        this(hostResources, null);
    }

    public AutoscalingTester(NodeResources hostResources, HostResourcesCalculator resourcesCalculator) {
        this(new Zone(Environment.prod, RegionName.from("us-east")), List.of(new Flavor("hostFlavor", hostResources)), resourcesCalculator);
        provisioningTester.makeReadyNodes(20, "hostFlavor", NodeType.host, 8);
        provisioningTester.activateTenantHosts();
    }

    public AutoscalingTester(Zone zone, List<Flavor> flavors) {
        this(zone, flavors, new MockHostResourcesCalculator(zone));
    }

    private AutoscalingTester(Zone zone, List<Flavor> flavors,
                              HostResourcesCalculator resourcesCalculator) {
        provisioningTester = new ProvisioningTester.Builder().zone(zone)
                                                             .flavors(flavors)
                                                             .resourcesCalculator(resourcesCalculator)
                                                             .hostProvisioner(zone.getCloud().dynamicProvisioning() ? new MockHostProvisioner(flavors) : null)
                                                             .build();

        hostResourcesCalculator = new MockHostResourcesCalculator(zone);
        db = MetricsDb.createTestInstance(provisioningTester.nodeRepository());
        autoscaler = new Autoscaler(db, nodeRepository());
    }

    public ProvisioningTester provisioning() { return provisioningTester; }

    public ApplicationId applicationId(String applicationName) {
        return ApplicationId.from("tenant1", applicationName, "instance1");
    }

    public ClusterSpec clusterSpec(ClusterSpec.Type type, String clusterId) {
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
            nodeRepository().nodes().setReady(List.of(host), Agent.system, getClass().getSimpleName());
    }

    public void deactivateRetired(ApplicationId application, ClusterSpec cluster, ClusterResources resources) {
        try (Mutex lock = nodeRepository().nodes().lock(application)){
            for (Node node : nodeRepository().nodes().list(Node.State.active).owner(application)) {
                if (node.allocation().get().membership().retired())
                    nodeRepository().nodes().write(node.with(node.allocation().get().removable(true)), lock);
            }
        }
        deploy(application, cluster, resources);
    }

    /**
     * Adds measurements with the given resource value and ideal values for the other resources,
     * scaled to take one node redundancy into account.
     * (I.e we adjust to measure a bit lower load than "naively" wanted to offset for the autoscaler
     * wanting to see the ideal load with one node missing.)
     *
     * @param otherResourcesLoad the load factor relative to ideal to use for other resources
     * @param count the number of measurements
     * @param applicationId the application we're adding measurements for all nodes of
     */
    public void addCpuMeasurements(float value, float otherResourcesLoad,
                                   int count, ApplicationId applicationId) {
        NodeList nodes = nodeRepository().nodes().list(Node.State.active).owner(applicationId);
        float oneExtraNodeFactor = (float)(nodes.size() - 1.0) / (nodes.size());
        for (int i = 0; i < count; i++) {
            clock().advance(Duration.ofMinutes(1));
            for (Node node : nodes) {
                float cpu = value * oneExtraNodeFactor;
                float memory  = (float) Resource.memory.idealAverageLoad() * otherResourcesLoad * oneExtraNodeFactor;
                float disk = (float) Resource.disk.idealAverageLoad() * otherResourcesLoad * oneExtraNodeFactor;
                db.addNodeMetrics(List.of(new Pair<>(node.hostname(), new NodeMetricSnapshot(clock().instant(),
                                                                                             cpu,
                                                                                             memory,
                                                                                             disk,
                                                                                             0,
                                                                                             true,
                                                                                             true,
                                                                                             0.0))));
            }
        }
    }

    /**
     * Adds measurements with the given resource value and ideal values for the other resources,
     * scaled to take one node redundancy into account.
     * (I.e we adjust to measure a bit lower load than "naively" wanted to offset for the autoscaler
     * wanting to see the ideal load with one node missing.)
     *
     * @param otherResourcesLoad the load factor relative to ideal to use for other resources
     * @param count the number of measurements
     * @param applicationId the application we're adding measurements for all nodes of
     */
    public void addMemMeasurements(float value, float otherResourcesLoad,
                                   int count, ApplicationId applicationId) {
        NodeList nodes = nodeRepository().nodes().list(Node.State.active).owner(applicationId);
        float oneExtraNodeFactor = (float)(nodes.size() - 1.0) / (nodes.size());
        for (int i = 0; i < count; i++) {
            clock().advance(Duration.ofMinutes(1));
            for (Node node : nodes) {
                float cpu  = (float) 0.2 * otherResourcesLoad * oneExtraNodeFactor;
                float memory = value * oneExtraNodeFactor;
                float disk = (float) Resource.disk.idealAverageLoad() * otherResourcesLoad * oneExtraNodeFactor;
                db.addNodeMetrics(List.of(new Pair<>(node.hostname(), new NodeMetricSnapshot(clock().instant(),
                                                                                             cpu,
                                                                                             memory,
                                                                                             disk,
                                                                                             0,
                                                                                             true,
                                                                                             true,
                                                                                             0.0))));
            }
        }
    }

    public void addMeasurements(float cpu, float memory, float disk, int generation, int count, ApplicationId applicationId)  {
        addMeasurements(cpu, memory, disk, generation, true, true, count, applicationId);
    }

    public void addMeasurements(float cpu, float memory, float disk, int generation, boolean inService, boolean stable,
                                int count, ApplicationId applicationId) {
        NodeList nodes = nodeRepository().nodes().list(Node.State.active).owner(applicationId);
        for (int i = 0; i < count; i++) {
            clock().advance(Duration.ofMinutes(1));
            for (Node node : nodes) {
                db.addNodeMetrics(List.of(new Pair<>(node.hostname(), new NodeMetricSnapshot(clock().instant(),
                                                                                             cpu,
                                                                                             memory,
                                                                                             disk,
                                                                                             generation,
                                                                                             inService,
                                                                                             stable,
                                                                                             0.0))));
            }
        }
    }

    public void storeReadShare(double currentReadShare, double maxReadShare, ApplicationId applicationId) {
        Application application = nodeRepository().applications().require(applicationId);
        application = application.with(application.status().withCurrentReadShare(currentReadShare)
                                                           .withMaxReadShare(maxReadShare));
        nodeRepository().applications().put(application, nodeRepository().nodes().lock(applicationId));
    }

    /** Creates a single redeployment event with bogus data except for the given duration */
    public void setScalingDuration(ApplicationId applicationId, ClusterSpec.Id clusterId, Duration duration) {
        Application application = nodeRepository().applications().require(applicationId);
        Cluster cluster = application.cluster(clusterId).get();
        cluster = new Cluster(clusterId,
                              cluster.exclusive(),
                              cluster.minResources(),
                              cluster.maxResources(),
                              cluster.suggestedResources(),
                              cluster.targetResources(),
                              List.of(), // Remove scaling events
                              cluster.autoscalingStatus());
        cluster = cluster.with(ScalingEvent.create(cluster.minResources(), cluster.minResources(),
                                                   0,
                                                   clock().instant().minus(Duration.ofDays(1).minus(duration))).withCompletion(clock().instant().minus(Duration.ofDays(1))));
        application = application.with(cluster);
        nodeRepository().applications().put(application, nodeRepository().nodes().lock(applicationId));
    }

    /** Creates the given number of measurements, spaced 5 minutes between, using the given function */
    public void addLoadMeasurements(ApplicationId application,
                                    ClusterSpec.Id cluster,
                                    int measurements,
                                    IntFunction<Double> queryRate,
                                    IntFunction<Double> writeRate) {
        Instant time = clock().instant();
        for (int i = 0; i < measurements; i++) {
            db.addClusterMetrics(application, Map.of(cluster, new ClusterMetricSnapshot(time, queryRate.apply(i), writeRate.apply(i))));
            time = time.plus(Duration.ofMinutes(5));
        }
    }

    /** Creates the given number of measurements, spaced 5 minutes between, using the given function */
    public void addQueryRateMeasurements(ApplicationId application,
                                         ClusterSpec.Id cluster,
                                         int measurements,
                                         IntFunction<Double> queryRate) {
        Instant time = clock().instant();
        for (int i = 0; i < measurements; i++) {
            db.addClusterMetrics(application, Map.of(cluster, new ClusterMetricSnapshot(time, queryRate.apply(i), 0.0)));
            time = time.plus(Duration.ofMinutes(5));
        }
    }

    public void clearQueryRateMeasurements(ApplicationId application, ClusterSpec.Id cluster) {
        db.clearClusterMetrics(application, cluster);
    }

    public Autoscaler.Advice autoscale(ApplicationId applicationId, ClusterSpec.Id clusterId,
                                                           ClusterResources min, ClusterResources max) {
        Application application = nodeRepository().applications().get(applicationId).orElse(Application.empty(applicationId))
                                                  .withCluster(clusterId, false, min, max);
        try (Mutex lock = nodeRepository().nodes().lock(applicationId)) {
            nodeRepository().applications().put(application, lock);
        }
        return autoscaler.autoscale(application, application.clusters().get(clusterId),
                                    nodeRepository().nodes().list(Node.State.active).owner(applicationId));
    }

    public Autoscaler.Advice suggest(ApplicationId applicationId, ClusterSpec.Id clusterId,
                                                           ClusterResources min, ClusterResources max) {
        Application application = nodeRepository().applications().get(applicationId).orElse(Application.empty(applicationId))
                                                  .withCluster(clusterId, false, min, max);
        try (Mutex lock = nodeRepository().nodes().lock(applicationId)) {
            nodeRepository().applications().put(application, lock);
        }
        return autoscaler.suggest(application, application.clusters().get(clusterId),
                                  nodeRepository().nodes().list(Node.State.active).owner(applicationId));
    }

    public ClusterResources assertResources(String message,
                                            int nodeCount, int groupCount,
                                            double approxCpu, double approxMemory, double approxDisk,
                                            Optional<ClusterResources> resources) {
        double delta = 0.0000000001;
        assertTrue(message, resources.isPresent());
        NodeResources nodeResources = resources.get().nodeResources();
        assertEquals("Node count in " + resources.get() + ": " + message, nodeCount, resources.get().nodes());
        assertEquals("Group count in " + resources.get() + ": " + message, groupCount, resources.get().groups());
        assertEquals("Cpu in " + resources.get() + ": " + message, approxCpu, Math.round(nodeResources.vcpu() * 10) / 10.0, delta);
        assertEquals("Memory in " + resources.get() + ": " + message, approxMemory, Math.round(nodeResources.memoryGb() * 10) / 10.0, delta);
        assertEquals("Disk in: " + resources.get() + ": "  + message, approxDisk, Math.round(nodeResources.diskGb() * 10) / 10.0, delta);
        return resources.get();
    }

    public ManualClock clock() {
        return provisioningTester.clock();
    }

    public NodeRepository nodeRepository() {
        return provisioningTester.nodeRepository();
    }

    public MetricsDb nodeMetricsDb() { return db; }

    private static class MockHostResourcesCalculator implements HostResourcesCalculator {

        private final Zone zone;

        public MockHostResourcesCalculator(Zone zone) {
            this.zone = zone;
        }

        @Override
        public NodeResources realResourcesOf(Nodelike node, NodeRepository nodeRepository, boolean exclusive) {
            if (zone.getCloud().dynamicProvisioning())
                return node.resources().withMemoryGb(node.resources().memoryGb() - 3);
            else
                return node.resources();
        }

        @Override
        public NodeResources advertisedResourcesOf(Flavor flavor) {
            if (zone.getCloud().dynamicProvisioning())
                return flavor.resources().withMemoryGb(flavor.resources().memoryGb() + 3);
            else
                return flavor.resources();
        }

        @Override
        public NodeResources requestToReal(NodeResources resources, boolean exclusive) {
            return resources.withMemoryGb(resources.memoryGb() - 3);
        }

        @Override
        public NodeResources realToRequest(NodeResources resources, boolean exclusive) {
            return resources.withMemoryGb(resources.memoryGb() + 3);
        }

        @Override
        public long thinPoolSizeInBase2Gb(NodeType nodeType, boolean sharedHost) { return 0; }

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

            return flavorResources.justNumbers().equals(resources.justNumbers());
        }

    }

}
