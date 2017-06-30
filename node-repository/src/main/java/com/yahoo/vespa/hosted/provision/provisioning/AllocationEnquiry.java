package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.lang.MutableInteger;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * @author smorgrav
 */
public class AllocationEnquiry {

    private final List<Node> nodes = new ArrayList<>();
    private final NodeFlavors flavors;
    private int additionalHosts = 0;

    public AllocationEnquiry(NodeFlavors flavors, List<Node> initialNodes) {
        this.flavors = flavors;
        nodes.addAll(initialNodes);
    }

    public void addDockerHost(String flavorName, int count) {
        for (int i = 0; i < count; i++) {
            Flavor flavor = flavors.getFlavorOrThrow(flavorName);
            additionalHosts += 1;
            nodes.add(node("docker" + additionalHosts, flavor, Optional.empty(), Optional.empty()));
        }
    }

    public boolean addCluster(String id, int count, String flavorName) {
        Flavor flavor = flavors.getFlavorOrThrow(flavorName);
        NodeSpec.CountNodeSpec nodeSpec = new NodeSpec.CountNodeSpec(count, flavor);
        NodeAllocation allocation = new NodeAllocation(app(id), cluster(), nodeSpec, new MutableInteger(0), Clock.systemUTC());

        List<Node> accepted = DockerAllocator.allocateNewDockerNodes(allocation,
                nodeSpec,
                new ArrayList<>(nodes),
                new ArrayList<>(nodes),
                flavors,
                flavor,
                2,
                (nodes, message)-> {});

        if (allocation.fullfilled()) {
            nodes.addAll(accepted);
        }

        return allocation.fullfilled();
    }

    public Map<String, Integer> getClusterCapacity() {
        Map<String, Integer> clusterCapacity = new HashMap<>();
        DockerHostCapacity capacity = new DockerHostCapacity(nodes);
        for (Flavor flavor : flavors.getFlavors()) {
            int nofHosts = (int)capacity.getNofHostsAvailableFor(flavor);
            clusterCapacity.put(flavor.name(), nofHosts);
        }
        return clusterCapacity;
    }

    public Map<String, Integer> getFlavorCapacity() {
        Map<String, Integer> flavorCapacity = new HashMap<>();
        DockerHostCapacity capacity = new DockerHostCapacity(nodes);
        for (Flavor flavor : flavors.getFlavors()) {
            int nofFlavors = capacity.freeCapacityInFlavorEquivalence(flavor);
            flavorCapacity.put(flavor.name(), nofFlavors);
        }
        return flavorCapacity;
    }

    private Node node(String hostname, Flavor flavor, Optional<String> parent, Optional<String> tenant) {
        return new Node("fake", Collections.singleton("127.0.0.1"),
                parent.isPresent() ? Collections.emptySet() : getAdditionalIP(), hostname, parent, flavor, Status.initial(),
                parent.isPresent() ? Node.State.ready : Node.State.active, allocation(tenant), History.empty(), parent.isPresent() ? NodeType.tenant : NodeType.host);
    }

    private Set<String> getAdditionalIP() {
        Set<String> h = new HashSet<String>();
        for (int i = 1; i < 33; i++) {
            h.add("::" + i);
        }
        return h;
    }

    private Optional<Allocation> allocation(Optional<String> tenant) {
        if (tenant.isPresent()) {
            Allocation allocation = new Allocation(app(tenant.get()), ClusterMembership.from("container/id1/3", new Version()), Generation.inital(), false);
            return Optional.of(allocation);
        }
        return Optional.empty();
    }

    private ApplicationId app(String tenant) {
        return new ApplicationId.Builder()
                .tenant(tenant)
                .applicationName("test")
                .instanceName("default").build();
    }

    private ClusterSpec cluster() {
        return ClusterSpec.from(ClusterSpec.Type.container, ClusterSpec.Id.from("test"), ClusterSpec.Group.from(1), Version.fromString("6.41"));
    }
}
