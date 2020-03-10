// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.collections.Tuple2;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.Zone;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorConfigBuilder;
import com.yahoo.vespa.hosted.provision.testutils.MockNameResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author mgimle
 */
public class CapacityCheckerTester {
    public static final Zone zone = new Zone(Environment.prod, RegionName.from("us-east"));

    // Components with state
    public final ManualClock clock = new ManualClock();
    public final NodeRepository nodeRepository;
    public CapacityChecker capacityChecker;

    CapacityCheckerTester() {
        Curator curator = new MockCurator();
        NodeFlavors f = new NodeFlavors(new FlavorConfigBuilder().build());
        nodeRepository = new NodeRepository(f, curator, clock, zone, new MockNameResolver().mockAnyLookup(),
                                            DockerImage.fromString("docker-registry.domain.tld:8080/dist/vespa"), true,
                                            new InMemoryFlagSource());
    }

    private void updateCapacityChecker() {
        this.capacityChecker = new CapacityChecker(this.nodeRepository);
    }

    List<NodeModel> createDistinctChildren(int amount, List<NodeResources> childResources) {
        String wantedVespaVersion = "7.67.9";
        List<String> tenants = List.of("foocom", "barinc");
        List<String> applications = List.of("ranking", "search");
        List<Tuple2<ClusterSpec.Type, String>> clusterSpecs = List.of(
                new Tuple2<>(ClusterSpec.Type.content, "content"),
                new Tuple2<>(ClusterSpec.Type.container, "suggest"),
                new Tuple2<>(ClusterSpec.Type.admin, "log"));
        int childCombinations = tenants.size() * applications.size() * clusterSpecs.size();
        List<NodeModel> distinctChildren = new ArrayList<>();

        for (int j = 0; j < amount;)
            for (var tenant : tenants)
                for (var application : applications)
                    for (var clusterSpec : clusterSpecs) {
                        if (j >= amount) continue;
                        NodeModel child = new NodeModel();
                        child.type = NodeType.tenant;
                        NodeResources cnr = childResources.get(j % childResources.size());
                        child.minCpuCores = cnr.vcpu();
                        child.minMainMemoryAvailableGb = cnr.memoryGb();
                        child.minDiskAvailableGb = cnr.diskGb();
                        child.fastDisk = true;
                        child.ipAddresses = Set.of();
                        child.additionalIpAddresses = Set.of();
                        child.owner = new NodeModel.OwnerModel();
                        child.owner.tenant = tenant + j / childCombinations;
                        child.owner.application = application;
                        child.owner.instance = "default";
                        child.membership = NodeModel.MembershipModel.from(clusterSpec.first, clusterSpec.second, 0);
                        child.wantedVespaVersion = wantedVespaVersion;
                        child.state = Node.State.active;
                        child.environment = Flavor.Type.DOCKER_CONTAINER;

                        distinctChildren.add(child);
                        j++;
                    }

        return distinctChildren;
    }

    List<Node> createHostsWithChildren(int childrenPerHost, List<NodeModel> distinctChildren, int amount, NodeResources excessCapacity, int excessIps) {
        List<Node> hosts = new ArrayList<>();
        int j = 0;
        for (int i = 0; i < amount; i++) {
            String parentRoot = ".not.a.real.hostname.yahoo.com";
            String parentName = "parent" + i;
            String hostname = parentName + parentRoot;

            List<NodeResources> childResources = new ArrayList<>();
            for (int k = 0; k < childrenPerHost; k++, j++) {
                NodeModel childModel = distinctChildren.get(j % distinctChildren.size());
                String childHostName = parentName + "-v6-" + k + parentRoot;
                childModel.id = childHostName;
                childModel.hostname = childHostName;
                childModel.ipAddresses = Set.of(String.format("%04X::%04X", i, k));
                childModel.membership.index = j / distinctChildren.size();
                childModel.parentHostname = Optional.of(hostname);

                Node childNode = createNodeFromModel(childModel);
                childResources.add(childNode.flavor().resources());
                hosts.add(childNode);
            }

            final int hostindex = i;
            Set<String> availableIps = IntStream.range(0, childrenPerHost + excessIps)
                    .mapToObj(n -> String.format("%04X::%04X", hostindex, n))
                    .collect(Collectors.toSet());

            NodeResources nr = containingNodeResources(childResources,
                    excessCapacity);
            Node node = nodeRepository.createNode(hostname, hostname,
                                                  new IP.Config(Set.of("::"), availableIps), Optional.empty(),
                                                  new Flavor(nr), Optional.empty(), NodeType.host);
            hosts.add(node);
        }
        return hosts;
    }

    List<Node> createEmptyHosts(int baseIndex, int amount, NodeResources capacity, int ips) {
        List<Node> hosts = new ArrayList<>();
        for (int i = baseIndex; i < baseIndex + amount; i++) {
            String parentRoot = ".empty.not.a.real.hostname.yahoo.com";
            String parentName = "parent" + i;
            String hostname = parentName + parentRoot;

            final int hostid = i;
            Set<String> availableIps = IntStream.range(0, ips)
                    .mapToObj(n -> String.format("%04X::%04X", hostid, n))
                    .collect(Collectors.toSet());
            Node node = nodeRepository.createNode(hostname, hostname,
                                                  new IP.Config(Set.of("::"), availableIps), Optional.empty(),
                                                  new Flavor(capacity), Optional.empty(), NodeType.host);
            hosts.add(node);
        }
        return hosts;
    }

    void createNodes(int childrenPerHost, int numDistinctChildren,
                     int numHosts, NodeResources hostExcessCapacity, int hostExcessIps,
                     int numEmptyHosts, NodeResources emptyHostExcessCapacity, int emptyHostExcessIps) {
        List<NodeResources> childResources = List.of(
                new NodeResources(1, 10, 100, 1)

        );
        createNodes(childrenPerHost, numDistinctChildren, childResources,
                numHosts, hostExcessCapacity, hostExcessIps,
                numEmptyHosts, emptyHostExcessCapacity, emptyHostExcessIps);
    }
    void createNodes(int childrenPerHost, int numDistinctChildren, List<NodeResources> childResources,
                     int numHosts, NodeResources hostExcessCapacity, int hostExcessIps,
                     int numEmptyHosts, NodeResources emptyHostExcessCapacity, int emptyHostExcessIps) {
        List<NodeModel> possibleChildren = createDistinctChildren(numDistinctChildren, childResources);

        List<Node> nodes = new ArrayList<>();
        nodes.addAll(createHostsWithChildren(childrenPerHost, possibleChildren, numHosts, hostExcessCapacity, hostExcessIps));
        nodes.addAll(createEmptyHosts(numHosts, numEmptyHosts, emptyHostExcessCapacity, emptyHostExcessIps));

        nodeRepository.addNodes(nodes, Agent.system);
        updateCapacityChecker();
    }

    NodeResources containingNodeResources(List<NodeResources> resources, NodeResources excessCapacity) {
        NodeResources usedByChildren = resources.stream()
                .reduce(new NodeResources(0, 0, 0, 0), NodeResources::add);
        return usedByChildren.add(excessCapacity);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NodeModel {
        static class MembershipModel {
            @JsonProperty ClusterSpec.Type clustertype;
            @JsonProperty String clusterid;
            @JsonProperty String group;
            @JsonProperty int index;
            @JsonProperty boolean retired;

            public static MembershipModel from(ClusterSpec.Type type, String id, int index) {
                MembershipModel m = new MembershipModel();
                m.clustertype = type;
                m.clusterid = id;
                m.group = "0";
                m.index = index;
                m.retired = false;
                return m;
            }
            public String toString() {
                return String.format("%s/%s/%s/%d%s", clustertype, clusterid, group, index, retired ? "/retired" : "");
            }
        }
        static class OwnerModel {
            @JsonProperty String tenant;
            @JsonProperty String application;
            @JsonProperty String instance;
        }

        @JsonProperty String id;
        @JsonProperty String hostname;
        @JsonProperty NodeType type;
        Optional<String> parentHostname = Optional.empty();
        @JsonSetter("parentHostname")
        private void setParentHostname(String name) { this.parentHostname = Optional.ofNullable(name); }
        @JsonGetter("parentHostname")
        String getParentHostname() { return parentHostname.orElse(null); }
        @JsonProperty double minDiskAvailableGb;
        @JsonProperty double minMainMemoryAvailableGb;
        @JsonProperty double minCpuCores;
        @JsonProperty double bandwidth;
        @JsonProperty boolean fastDisk;
        @JsonProperty Set<String> ipAddresses;
        @JsonProperty Set<String> additionalIpAddresses;

        @JsonProperty OwnerModel owner;
        @JsonProperty MembershipModel membership;
        @JsonProperty String wantedVespaVersion;
        @JsonProperty Node.State state;
        @JsonProperty Flavor.Type environment;
    }

    static class NodeRepositoryModel {
        @JsonProperty
        List<NodeModel> nodes;
    }

    Node createNodeFromModel(NodeModel nodeModel) {
        ClusterMembership membership = null;
        ApplicationId owner = null;
        if (nodeModel.membership != null && nodeModel.owner != null) {
            membership = ClusterMembership.from(
                    nodeModel.membership.toString(),
                    Version.fromString(nodeModel.wantedVespaVersion));
            owner = ApplicationId.from(nodeModel.owner.tenant, nodeModel.owner.application, nodeModel.owner.instance);
        }

        NodeResources.DiskSpeed diskSpeed;
        NodeResources nr = new NodeResources(nodeModel.minCpuCores, nodeModel.minMainMemoryAvailableGb,
                nodeModel.minDiskAvailableGb, nodeModel.bandwidth * 1000,
                nodeModel.fastDisk ? NodeResources.DiskSpeed.fast : NodeResources.DiskSpeed.slow);
        Flavor f = new Flavor(nr);

        Node node = nodeRepository.createNode(nodeModel.id, nodeModel.hostname,
                                              new IP.Config(nodeModel.ipAddresses, nodeModel.additionalIpAddresses),
                                              nodeModel.parentHostname, f, Optional.empty(), nodeModel.type);

        if (membership != null) {
            return node.allocate(owner, membership, node.flavor().resources(), Instant.now());
        } else {
            return node;
        }
    }

    public void populateNodeRepositoryFromJsonFile(Path path) throws IOException {
        byte[] jsonData = Files.readAllBytes(path);
        ObjectMapper om = new ObjectMapper();

        NodeRepositoryModel repositoryModel = om.readValue(jsonData, NodeRepositoryModel.class);
        List<NodeModel> nmods = repositoryModel.nodes;

        List<Node> nodes = new ArrayList<>();
        for (var nmod : nmods) {
            if (nmod.type != NodeType.host && nmod.type != NodeType.tenant) continue;

            nodes.add(createNodeFromModel(nmod));
        }

        nodeRepository.addNodes(nodes, Agent.system);
        updateCapacityChecker();
    }

}
