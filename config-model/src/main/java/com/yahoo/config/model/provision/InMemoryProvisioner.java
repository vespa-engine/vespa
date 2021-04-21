// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.collections.ListMap;
import com.yahoo.collections.Pair;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.api.Provisioned;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.ProvisionLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * In memory host provisioner for testing only.
 * NB! ATM cannot be reused after allocate has been called.
 *
 * @author hmusum
 * @author bratseth
 */
public class InMemoryProvisioner implements HostProvisioner {

    public static final NodeResources defaultResources = new NodeResources(1, 3, 10, 1);

    /**
     * If this is true an exception is thrown when all nodes are used.
     * If false this will simply return nodes best effort, preferring to satisfy the
     * number of groups requested when possible.
     */
    private final boolean failOnOutOfCapacity;

    /** Hosts which should be returned as retired */
    private final Set<String> retiredHostNames;

    /** If false, nodes returned will have the resources of the host, if true node resources will be as requested */
    private final boolean sharedHosts;

    /** Free hosts of each resource size */
    private final ListMap<NodeResources, Host> freeNodes = new ListMap<>();
    private final Map<ClusterSpec, List<HostSpec>> allocations = new LinkedHashMap<>();

    /** Indexes must be unique across all groups in a cluster */
    private final Map<Pair<ClusterSpec.Type, ClusterSpec.Id>, Integer> nextIndexInCluster = new HashMap<>();

    /** Use this index as start index for all clusters */
    private final int startIndexForClusters;

    private final boolean useMaxResources;

    private Provisioned provisioned = new Provisioned();

    /** Creates this with a number of nodes with resources 1, 3, 9, 1 */
    public InMemoryProvisioner(int nodeCount, boolean sharedHosts) {
        this(nodeCount, defaultResources, sharedHosts);
    }

    /** Creates this with a number of nodes with given resources */
    public InMemoryProvisioner(int nodeCount, NodeResources resources, boolean sharedHosts) {
        this(Map.of(resources, createHostInstances(nodeCount)), true, false, sharedHosts, 0);
    }

    /** Creates this with a set of host names of the flavor 'default' */
    public InMemoryProvisioner(boolean failOnOutOfCapacity, boolean sharedHosts, String... hosts) {
        this(Map.of(defaultResources, toHostInstances(hosts)), failOnOutOfCapacity, false, sharedHosts, 0);
    }

    /** Creates this with a set of host names of the flavor 'default' */
    public InMemoryProvisioner(boolean failOnOutOfCapacity, boolean sharedHosts, List<String> hosts) {
        this(Map.of(defaultResources, toHostInstances(hosts.toArray(new String[0]))), failOnOutOfCapacity, false, sharedHosts, 0);
    }

    /** Creates this with a set of hosts of the flavor 'default' */
    public InMemoryProvisioner(Hosts hosts, boolean failOnOutOfCapacity, boolean sharedHosts, String ... retiredHostNames) {
        this(Map.of(defaultResources, hosts.asCollection()), failOnOutOfCapacity, false, sharedHosts, 0, retiredHostNames);
    }

    /** Creates this with a set of hosts of the flavor 'default' */
    public InMemoryProvisioner(Hosts hosts, boolean failOnOutOfCapacity, boolean sharedHosts, int startIndexForClusters, String ... retiredHostNames) {
        this(Map.of(defaultResources, hosts.asCollection()), failOnOutOfCapacity, false, sharedHosts, startIndexForClusters, retiredHostNames);
    }

    public InMemoryProvisioner(Map<NodeResources, Collection<Host>> hosts,
                               boolean failOnOutOfCapacity,
                               boolean useMaxResources,
                               boolean sharedHosts,
                               int startIndexForClusters,
                               String ... retiredHostNames) {
        this.failOnOutOfCapacity = failOnOutOfCapacity;
        this.useMaxResources = useMaxResources;
        for (Map.Entry<NodeResources, Collection<Host>> hostsWithResources : hosts.entrySet())
            for (Host host : hostsWithResources.getValue())
                freeNodes.put(hostsWithResources.getKey(), host);
        this.sharedHosts = sharedHosts;
        this.startIndexForClusters = startIndexForClusters;
        this.retiredHostNames = Set.of(retiredHostNames);
    }

    private static Collection<Host> toHostInstances(String[] hostnames) {
        return Arrays.stream(hostnames).map(Host::new).collect(Collectors.toList());
    }

    private static Collection<Host> createHostInstances(int hostCount) {
        return IntStream.range(1, hostCount + 1).mapToObj(i -> new Host("host" + i)).collect(Collectors.toList());
    }

    /** Returns the current allocations of this as a mutable map */
    public Map<ClusterSpec, List<HostSpec>> allocations() { return allocations; }

    @Override
    public HostSpec allocateHost(String alias) {
        List<Host> defaultHosts = freeNodes.get(defaultResources);
        if (defaultHosts.isEmpty()) throw new IllegalArgumentException("No more hosts with default resources available");
        Host newHost = freeNodes.removeValue(defaultResources, 0);
        return new HostSpec(newHost.hostname(), List.of(alias), Optional.empty());
    }

    @Override
    public List<HostSpec> prepare(ClusterSpec cluster, Capacity requested, ProvisionLogger logger) {
        provisioned.add(cluster.id(), requested);
        if (useMaxResources)
            return prepare(cluster, requested.maxResources(), requested.isRequired(), requested.canFail());
        else
            return prepare(cluster, requested.minResources(), requested.isRequired(), requested.canFail());
    }

    public List<HostSpec> prepare(ClusterSpec cluster, ClusterResources requested, boolean required, boolean canFail) {
        if (cluster.group().isPresent() && requested.groups() > 1)
            throw new IllegalArgumentException("Cannot both be specifying a group and ask for groups to be created");

        int capacity = failOnOutOfCapacity || required
                       ? requested.nodes()
                       : Math.min(requested.nodes(), freeNodes.get(defaultResources).size() + totalAllocatedTo(cluster));
        int groups = requested.groups() > capacity ? capacity : requested.groups();

        List<HostSpec> allocation = new ArrayList<>();
        if (groups == 1) {
            allocation.addAll(allocateHostGroup(cluster.with(Optional.of(ClusterSpec.Group.from(0))),
                                                requested.nodeResources(),
                                                capacity,
                                                startIndexForClusters,
                                                canFail));
        }
        else {
            for (int i = 0; i < groups; i++) {
                allocation.addAll(allocateHostGroup(cluster.with(Optional.of(ClusterSpec.Group.from(i))),
                                                    requested.nodeResources(),
                                                    capacity / groups,
                                                    allocation.size(),
                                                    canFail));
            }
        }
        for (ListIterator<HostSpec> i = allocation.listIterator(); i.hasNext(); ) {
            HostSpec host = i.next();
            if (retiredHostNames.contains(host.hostname()))
                i.set(retire(host));
        }

        return allocation;
    }

    /** Create a new provisioned instance to record provision requests to this and returns it */
    public Provisioned startProvisionedRecording() {
        provisioned = new Provisioned();
        return provisioned;
    }

    private HostSpec retire(HostSpec host) {
        return new HostSpec(host.hostname(),
                            host.realResources(),
                            host.advertisedResources(),
                            host.requestedResources().orElse(NodeResources.unspecified()),
                            host.membership().get().retire(),
                            host.version(),
                            Optional.empty(),
                            host.dockerImageRepo());
    }

    private List<HostSpec> allocateHostGroup(ClusterSpec clusterGroup, NodeResources requestedResources,
                                             int nodesInGroup, int startIndex, boolean canFail) {
        List<HostSpec> allocation = allocations.getOrDefault(clusterGroup, new ArrayList<>());
        allocations.put(clusterGroup, allocation);

        // Check if the current allocations are compatible with the new request
        for (int i = allocation.size() - 1; i >= 0; i--) {
            NodeResources currentResources = allocation.get(0).advertisedResources();
            if (currentResources.isUnspecified() || requestedResources.isUnspecified()) continue;
            if ( ! currentResources.compatibleWith(requestedResources)) {
                HostSpec removed = allocation.remove(i);
                freeNodes.put(currentResources, new Host(removed.hostname())); // Return the node back to free pool
            }
        }

        int nextIndex = nextIndexInCluster.getOrDefault(new Pair<>(clusterGroup.type(), clusterGroup.id()), startIndex);
        while (allocation.size() < nodesInGroup) {
            // Find the smallest host that can fit the requested resources
            Optional<NodeResources> hostResources = freeNodes.keySet().stream()
                    .sorted(new MemoryDiskCpu())
                    .filter(resources -> requestedResources.isUnspecified() || resources.satisfies(requestedResources))
                    .findFirst();
            if (hostResources.isEmpty()) {
                if (canFail)
                    throw new IllegalArgumentException("Insufficient capacity for " + requestedResources);
                else
                    break; // ¯\_(ツ)_/¯
            }

            Host newHost = freeNodes.removeValue(hostResources.get(), 0);
            if (freeNodes.get(hostResources.get()).isEmpty()) freeNodes.removeAll(hostResources.get());
            ClusterMembership membership = ClusterMembership.from(clusterGroup, nextIndex++);
            NodeResources resources = sharedHosts ? requestedResources : hostResources.get();
            allocation.add(new HostSpec(newHost.hostname(),
                                        resources, resources, requestedResources,
                                        membership,
                                        newHost.version(), Optional.empty(),
                                        Optional.empty()));
        }
        nextIndexInCluster.put(new Pair<>(clusterGroup.type(), clusterGroup.id()), nextIndex);

        while (allocation.size() > nodesInGroup)
            allocation.remove(0);

        return allocation;
    }

    private int totalAllocatedTo(ClusterSpec cluster) {
        int count = 0;
        for (Map.Entry<ClusterSpec, List<HostSpec>> allocation : allocations.entrySet()) {
            if ( ! allocation.getKey().type().equals(cluster.type())) continue;
            if ( ! allocation.getKey().id().equals(cluster.id())) continue;
            count += allocation.getValue().size();
        }
        return count;
    }

    private static class MemoryDiskCpu implements Comparator<NodeResources> {

        @Override
        public int compare(NodeResources a, NodeResources b) {
            if (a.memoryGb() > b.memoryGb()) return 1;
            if (a.memoryGb() < b.memoryGb()) return -1;
            if (a.diskGb() > b.diskGb()) return 1;
            if (a.diskGb() < b.diskGb()) return -1;
            if (a.vcpu() > b.vcpu()) return 1;
            if (a.vcpu() < b.vcpu()) return -1;
            return 0;
        }
    }
}
