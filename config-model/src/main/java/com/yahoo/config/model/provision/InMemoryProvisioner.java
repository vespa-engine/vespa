// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.provision;

import com.yahoo.collections.ListMap;
import com.yahoo.collections.Pair;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ProvisionLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * In memory host provisioner. NB! ATM cannot be reused after allocate has been called.
 *
 * @author hmusum
 * @author bratseth
 */
public class InMemoryProvisioner implements HostProvisioner {

    /**
     * If this is true an exception is thrown when all nodes are used.
     * If false this will simply return nodes best effort, preferring to satisfy the
     * number of groups requested when possible.
     */
    private final boolean failOnOutOfCapacity;

    /** Hosts which should be returned as retired */
    private final Set<String> retiredHostNames;

    /** Free hosts of each flavor */
    private final ListMap<String, Host> freeNodes = new ListMap<>();
    private final Map<String, HostSpec> legacyMapping = new LinkedHashMap<>();
    private final Map<ClusterSpec, List<HostSpec>> allocations = new LinkedHashMap<>();

    /** Indexes must be unique across all groups in a cluster */
    private final Map<Pair<ClusterSpec.Type,ClusterSpec.Id>, Integer> nextIndexInCluster = new HashMap<>();

    /** Use this index as start index for all clusters */
    private final int startIndexForClusters;

    /** Creates this with a number of nodes of the flavor 'default' */
    public InMemoryProvisioner(int nodeCount) {
        this(Collections.singletonMap("default", createHostInstances(nodeCount)), true, 0);
    }

    /** Creates this with a set of host names of the flavor 'default' */
    public InMemoryProvisioner(boolean failOnOutOfCapacity, String... hosts) {
        this(Collections.singletonMap("default", toHostInstances(hosts)), failOnOutOfCapacity, 0);
    }

    /** Creates this with a set of hosts of the flavor 'default' */
    public InMemoryProvisioner(Hosts hosts, boolean failOnOutOfCapacity, String ... retiredHostNames) {
        this(Collections.singletonMap("default", hosts.asCollection()), failOnOutOfCapacity, 0, retiredHostNames);
    }

    /** Creates this with a set of hosts of the flavor 'default' */
    public InMemoryProvisioner(Hosts hosts, boolean failOnOutOfCapacity, int startIndexForClusters, String ... retiredHostNames) {
        this(Collections.singletonMap("default", hosts.asCollection()), failOnOutOfCapacity, startIndexForClusters, retiredHostNames);
    }

    public InMemoryProvisioner(Map<String, Collection<Host>> hosts, boolean failOnOutOfCapacity, int startIndexForClusters, String ... retiredHostNames) {
        this.failOnOutOfCapacity = failOnOutOfCapacity;
        for (Map.Entry<String, Collection<Host>> hostsOfFlavor : hosts.entrySet())
            for (Host host : hostsOfFlavor.getValue())
                freeNodes.put(hostsOfFlavor.getKey(), host);
        this.retiredHostNames = new HashSet<>(Arrays.asList(retiredHostNames));
        this.startIndexForClusters = startIndexForClusters;
    }

    private static Collection<Host> toHostInstances(String[] hostnames) {
        List<Host> hosts = new ArrayList<>();
        for (String hostname : hostnames) {
            hosts.add(new Host(hostname));
        }
        return hosts;
    }

    private static Collection<Host> createHostInstances(int hostCount) {
        List<Host> hosts = new ArrayList<>();
        for (int i = 1; i <= hostCount; i++) {
            hosts.add(new Host("host" + i));
        }
        return hosts;
    }

    @Override
    public HostSpec allocateHost(String alias) {
        if (legacyMapping.containsKey(alias)) return legacyMapping.get(alias);
        List<Host> defaultHosts = freeNodes.get("default");
        if (defaultHosts.isEmpty()) throw new IllegalArgumentException("No more hosts of default flavor available");
        Host newHost = freeNodes.removeValue("default", 0);
        HostSpec hostSpec = new HostSpec(newHost.hostname(), newHost.aliases(), newHost.flavor(), Optional.empty(), newHost.version());
        legacyMapping.put(alias, hostSpec);
        return hostSpec;
    }

    @Override
    public List<HostSpec> prepare(ClusterSpec cluster, Capacity requestedCapacity, int groups, ProvisionLogger logger) {
        if (cluster.group().isPresent() && groups > 1)
            throw new IllegalArgumentException("Cannot both be specifying a group and ask for groups to be created");
        if (requestedCapacity.nodeCount() % groups != 0)
            throw new IllegalArgumentException("Requested " + requestedCapacity.nodeCount() + " nodes in " +
                                               groups + " groups, but the node count is not divisible into this number of groups");

        int capacity = failOnOutOfCapacity || requestedCapacity.isRequired() 
                       ? requestedCapacity.nodeCount() 
                       : Math.min(requestedCapacity.nodeCount(), freeNodes.get("default").size() + totalAllocatedTo(cluster));
        if (groups > capacity)
            groups = capacity;

        String flavor = requestedCapacity.flavor().orElse("default");

        List<HostSpec> allocation = new ArrayList<>();
        if (groups == 1) {
            allocation.addAll(allocateHostGroup(cluster.with(Optional.of(ClusterSpec.Group.from(0))),
                                                flavor,
                                                capacity,
                                                startIndexForClusters));
        }
        else {
            for (int i = 0; i < groups; i++) {
                allocation.addAll(allocateHostGroup(cluster.with(Optional.of(ClusterSpec.Group.from(i))),
                                                    flavor,
                                                    capacity / groups,
                                                    allocation.size()));
            }
        }
        for (ListIterator<HostSpec> i = allocation.listIterator(); i.hasNext(); ) {
            HostSpec host = i.next();
            if (retiredHostNames.contains(host.hostname()))
                i.set(retire(host));
        }
        return allocation;
    }

    private HostSpec retire(HostSpec host) {
        return new HostSpec(host.hostname(),
                            host.aliases(),
                            host.flavor(),
                            Optional.of(host.membership().get().retire()),
                            host.version());
    }

    private List<HostSpec> allocateHostGroup(ClusterSpec clusterGroup, String flavor, int nodesInGroup, int startIndex) {
        List<HostSpec> allocation = allocations.getOrDefault(clusterGroup, new ArrayList<>());
        allocations.put(clusterGroup, allocation);

        int nextIndex = nextIndexInCluster.getOrDefault(new Pair<>(clusterGroup.type(), clusterGroup.id()), startIndex);
        while (allocation.size() < nodesInGroup) {
            if (freeNodes.get(flavor).isEmpty()) throw new IllegalArgumentException("Insufficient capacity of flavor '" + flavor + "'");
            Host newHost = freeNodes.removeValue(flavor, 0);
            ClusterMembership membership = ClusterMembership.from(clusterGroup, nextIndex++);
            allocation.add(new HostSpec(newHost.hostname(), newHost.aliases(), newHost.flavor(), Optional.of(membership), newHost.version()));
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

}
