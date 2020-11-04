// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
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
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.Nodelike;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostResourcesCalculator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisioningTester;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

class AutoscalingTester {

    private final ProvisioningTester provisioningTester;
    private final Autoscaler autoscaler;
    private final MetricsDb db;
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
        List<HostSpec> hosts = provisioningTester.prepare(application, cluster, Capacity.from(new ClusterResources(nodes, groups, resources)));
        for (HostSpec host : hosts)
            makeReady(host.hostname());
        provisioningTester.activateTenantHosts();
        provisioningTester.activate(application, hosts);
        return hosts;
    }

    public void makeReady(String hostname) {
        Node node = nodeRepository().getNode(hostname).get();
        provisioningTester.patchNode(node, (n) -> n.with(new IP.Config(Set.of("::" + 0 + ":0"), Set.of())));
        Node host = nodeRepository().getNode(node.parentHostname().get()).get();
        host = host.with(new IP.Config(Set.of("::" + 0 + ":0"), Set.of("::" + 0 + ":2")));
        if (host.state() == Node.State.provisioned)
            nodeRepository().setReady(List.of(host), Agent.system, getClass().getSimpleName());
    }

    public void deactivateRetired(ApplicationId application, ClusterSpec cluster, ClusterResources resources) {
        try (Mutex lock = nodeRepository().lock(application)){
            for (Node node : nodeRepository().getNodes(application, Node.State.active)) {
                if (node.allocation().get().membership().retired())
                    nodeRepository().write(node.with(node.allocation().get().removable(true)), lock);
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
        List<Node> nodes = nodeRepository().getNodes(applicationId, Node.State.active);
        float oneExtraNodeFactor = (float)(nodes.size() - 1.0) / (nodes.size());
        for (int i = 0; i < count; i++) {
            clock().advance(Duration.ofMinutes(1));
            for (Node node : nodes) {
                float cpu = value * oneExtraNodeFactor;
                float memory  = (float) Resource.memory.idealAverageLoad() * otherResourcesLoad * oneExtraNodeFactor;
                float disk = (float) Resource.disk.idealAverageLoad() * otherResourcesLoad * oneExtraNodeFactor;
                db.add(List.of(new Pair<>(node.hostname(), new MetricSnapshot(clock().instant(),
                                                                              cpu,
                                                                              memory,
                                                                              disk,
                                                                              0))));
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
        List<Node> nodes = nodeRepository().getNodes(applicationId, Node.State.active);
        float oneExtraNodeFactor = (float)(nodes.size() - 1.0) / (nodes.size());
        for (int i = 0; i < count; i++) {
            clock().advance(Duration.ofMinutes(1));
            for (Node node : nodes) {
                float cpu  = (float) Resource.cpu.idealAverageLoad() * otherResourcesLoad * oneExtraNodeFactor;
                float memory = value * oneExtraNodeFactor;
                float disk = (float) Resource.disk.idealAverageLoad() * otherResourcesLoad * oneExtraNodeFactor;
                db.add(List.of(new Pair<>(node.hostname(), new MetricSnapshot(clock().instant(),
                                                                              cpu,
                                                                              memory,
                                                                              disk,
                                                                              0))));
            }
        }
    }

    public void addMeasurements(float cpu, float memory, float disk, int generation, int count, ApplicationId applicationId) {
        List<Node> nodes = nodeRepository().getNodes(applicationId, Node.State.active);
        for (int i = 0; i < count; i++) {
            clock().advance(Duration.ofMinutes(1));
            for (Node node : nodes) {
                db.add(List.of(new Pair<>(node.hostname(), new MetricSnapshot(clock().instant(),
                                                                              cpu,
                                                                              memory,
                                                                              disk,
                                                                              generation))));
            }
        }
    }

    public Autoscaler.Advice autoscale(ApplicationId applicationId, ClusterSpec.Id clusterId,
                                                           ClusterResources min, ClusterResources max) {
        Application application = nodeRepository().applications().get(applicationId).orElse(new Application(applicationId))
                                                  .withCluster(clusterId, false, min, max);
        try (Mutex lock = nodeRepository().lock(applicationId)) {
            nodeRepository().applications().put(application, lock);
        }
        return autoscaler.autoscale(application.clusters().get(clusterId),
                                    nodeRepository().getNodes(applicationId, Node.State.active));
    }

    public Autoscaler.Advice suggest(ApplicationId applicationId, ClusterSpec.Id clusterId,
                                                           ClusterResources min, ClusterResources max) {
        Application application = nodeRepository().applications().get(applicationId).orElse(new Application(applicationId))
                                                  .withCluster(clusterId, false, min, max);
        try (Mutex lock = nodeRepository().lock(applicationId)) {
            nodeRepository().applications().put(application, lock);
        }
        return autoscaler.suggest(application.clusters().get(clusterId),
                                  nodeRepository().getNodes(applicationId, Node.State.active));
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
        public NodeResources realResourcesOf(Nodelike node, NodeRepository nodeRepository) {
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
        public NodeResources realToRequest(NodeResources resources) {
            return resources.withMemoryGb(resources.memoryGb() + 3);
        }

        @Override
        public long thinPoolSizeInBase2Gb(NodeType nodeType) { return 0; }

    }

    private class MockHostProvisioner implements HostProvisioner {

        private final List<Flavor> hostFlavors;

        public MockHostProvisioner(List<Flavor> hostFlavors) {
            this.hostFlavors = hostFlavors;
        }

        @Override
        public List<ProvisionedHost> provisionHosts(List<Integer> provisionIndexes, NodeResources resources,
                                                    ApplicationId applicationId, Version osVersion,
                                                    HostSharing sharing) {
            Flavor hostFlavor = hostFlavors.stream().filter(f -> matches(f, resources)).findAny()
                                           .orElseThrow(() -> new RuntimeException("No flavor matching " + resources + ". Flavors: " + hostFlavors));

            List<ProvisionedHost> hosts = new ArrayList<>();
            for (int index : provisionIndexes) {
                hosts.add(new ProvisionedHost("host" + index,
                                              "hostname" + index,
                                              hostFlavor,
                                              Optional.empty(),
                                              "nodename" + index,
                                              resources,
                                              osVersion));
            }
            return hosts;
        }

        @Override
        public List<Node> provision(Node host, Set<Node> children) throws FatalProvisioningException {
            throw new RuntimeException("Not implemented");
        }

        @Override
        public void deprovision(Node host) {
            throw new RuntimeException("Not implemented");
        }

        private boolean matches(Flavor flavor, NodeResources resources) {
            NodeResources flavorResources = hostResourcesCalculator.advertisedResourcesOf(flavor);
            if (flavorResources.storageType() == NodeResources.StorageType.remote
                && resources.diskGb() <= flavorResources.diskGb())
                flavorResources = flavorResources.withDiskGb(resources.diskGb());

            return flavorResources.justNumbers().equals(resources.justNumbers());
        }

    }

}
