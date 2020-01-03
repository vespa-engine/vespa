// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.exception.LoadBalancerServiceException;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.lb.Real;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Provisions and configures application load balancers.
 *
 * @author mpolden
 */
// Load balancer state transitions:
// 1) (new) -> reserved -> active
// 2) active | reserved -> inactive
// 3) inactive -> active | (removed)
public class LoadBalancerProvisioner {

    private static final Logger log = Logger.getLogger(LoadBalancerProvisioner.class.getName());

    private final NodeRepository nodeRepository;
    private final CuratorDatabaseClient db;
    private final LoadBalancerService service;

    public LoadBalancerProvisioner(NodeRepository nodeRepository, LoadBalancerService service) {
        this.nodeRepository = nodeRepository;
        this.db = nodeRepository.database();
        this.service = service;
        // Read and write all load balancers to make sure they are stored in the latest version of the serialization format
        try (var lock = db.lockLoadBalancers()) {
            for (var id : db.readLoadBalancerIds()) {
                var loadBalancer = db.readLoadBalancer(id);
                loadBalancer.ifPresent(db::writeLoadBalancer);
            }
        }
    }

    /**
     * Prepare a load balancer for given application and cluster.
     *
     * If a load balancer for the cluster already exists, it will be reconfigured based on the currently allocated
     * nodes. It's state will remain unchanged.
     *
     * If no load balancer exists, a new one will be provisioned in {@link LoadBalancer.State#reserved}.
     *
     * Calling this for irrelevant node or cluster types is a no-op.
     */
    public void prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requestedNodes) {
        if (requestedNodes.type() != NodeType.tenant) return; // Nothing to provision for this node type
        if (!cluster.type().isContainer()) return; // Nothing to provision for this cluster type
        try (var loadBalancersLock = db.lockLoadBalancers()) {
            provision(application, cluster.id(), false, loadBalancersLock);
        }
    }

    /**
     * Activate load balancer for given application and cluster.
     *
     * If a load balancer for the cluster already exists, it will be reconfigured based on the currently allocated
     * nodes and the load balancer itself will be moved to {@link LoadBalancer.State#active}.
     *
     * Load balancers for clusters that are no longer in given clusters are deactivated.
     *
     * Calling this when no load balancer has been prepared for given cluster is a no-op.
     */
    public void activate(ApplicationId application, Set<ClusterSpec> clusters,
                         @SuppressWarnings("unused") Mutex applicationLock, NestedTransaction transaction) {
        try (var loadBalancersLock = db.lockLoadBalancers()) {
            var containerClusters = containerClusterOf(clusters);
            for (var clusterId : containerClusters) {
                // Provision again to ensure that load balancer instance is re-configured with correct nodes
                provision(application, clusterId, true, loadBalancersLock);
            }
            // Deactivate any surplus load balancers, i.e. load balancers for clusters that have been removed
            var surplusLoadBalancers = surplusLoadBalancersOf(application, containerClusters);
            deactivate(surplusLoadBalancers, transaction);
        }
    }

    /**
     * Deactivate all load balancers assigned to given application. This is a no-op if an application does not have any
     * load balancer(s).
     */
    public void deactivate(ApplicationId application, NestedTransaction transaction) {
        try (var applicationLock = nodeRepository.lock(application)) {
            try (Mutex loadBalancersLock = db.lockLoadBalancers()) {
                deactivate(nodeRepository.loadBalancers().owner(application).asList(), transaction);
            }
        }
    }

    /** Returns load balancers of given application that are no longer referenced by given clusters */
    private List<LoadBalancer> surplusLoadBalancersOf(ApplicationId application, Set<ClusterSpec.Id> activeClusters) {
        var activeLoadBalancersByCluster = nodeRepository.loadBalancers()
                                                         .owner(application)
                                                         .in(LoadBalancer.State.active)
                                                         .asList()
                                                         .stream()
                                                         .collect(Collectors.toMap(lb -> lb.id().cluster(),
                                                                                   Function.identity()));
        var surplus = new ArrayList<LoadBalancer>();
        for (var kv : activeLoadBalancersByCluster.entrySet()) {
            if (activeClusters.contains(kv.getKey())) continue;
            surplus.add(kv.getValue());
        }
        return Collections.unmodifiableList(surplus);
    }

    private void deactivate(List<LoadBalancer> loadBalancers, NestedTransaction transaction) {
        var now = nodeRepository.clock().instant();
        var deactivatedLoadBalancers = loadBalancers.stream()
                                                    .map(lb -> lb.with(LoadBalancer.State.inactive, now))
                                                    .collect(Collectors.toList());
        db.writeLoadBalancers(deactivatedLoadBalancers, transaction);
    }


    /** Idempotently provision a load balancer for given application and cluster */
    private void provision(ApplicationId application, ClusterSpec.Id clusterId, boolean activate,
                           @SuppressWarnings("unused") Mutex loadBalancersLock) {
        var id = new LoadBalancerId(application, clusterId);
        var now = nodeRepository.clock().instant();
        var loadBalancer = db.readLoadBalancer(id);
        if (loadBalancer.isEmpty() && activate) return; // Nothing to activate as this load balancer was never prepared

        var force = loadBalancer.isPresent() && loadBalancer.get().state() != LoadBalancer.State.active;
        var instance = create(application, clusterId, allocatedContainers(application, clusterId), force);
        LoadBalancer newLoadBalancer;
        if (loadBalancer.isEmpty()) {
            newLoadBalancer = new LoadBalancer(id, instance, LoadBalancer.State.reserved, now);
        } else {
            var newState = activate ? LoadBalancer.State.active : loadBalancer.get().state();
            newLoadBalancer = loadBalancer.get().with(instance).with(newState, now);
        }
        db.writeLoadBalancer(newLoadBalancer);
    }

    private LoadBalancerInstance create(ApplicationId application, ClusterSpec.Id cluster, List<Node> nodes, boolean force) {
        var reals = new LinkedHashSet<Real>();
        for (var node : nodes) {
            for (var ip : reachableIpAddresses(node)) {
                reals.add(new Real(HostName.from(node.hostname()), ip));
            }
        }
        log.log(LogLevel.INFO, "Creating load balancer for " + cluster + " in " + application.toShortString() +
                               ", targeting: " + reals);
        try {
            return service.create(application, cluster, reals, force);
        } catch (Exception e) {
            throw new LoadBalancerServiceException("Failed to (re)configure load balancer for " + cluster + " in " +
                                                   application + ", targeting: " + reals + ". The operation will be " +
                                                   "retried on next deployment", e);
        }
    }

    /** Returns a list of active and reserved nodes of type container in given cluster */
    private List<Node> allocatedContainers(ApplicationId application, ClusterSpec.Id clusterId) {
        return new NodeList(nodeRepository.getNodes(NodeType.tenant, Node.State.reserved, Node.State.active))
                .owner(application)
                .filter(node -> node.state().isAllocated())
                .container()
                .filter(node -> node.allocation().get().membership().cluster().id().equals(clusterId))
                .asList();
    }

    /** Find IP addresses reachable by the load balancer service */
    private Set<String> reachableIpAddresses(Node node) {
        Set<String> reachable = new LinkedHashSet<>(node.ipAddresses());
        // Remove addresses unreachable by the load balancer service
        switch (service.protocol()) {
            case ipv4:
                reachable.removeIf(IP::isV6);
                break;
            case ipv6:
                reachable.removeIf(IP::isV4);
                break;
        }
        return reachable;
    }

    private static Set<ClusterSpec.Id> containerClusterOf(Set<ClusterSpec> clusters) {
        return clusters.stream()
                       .filter(c -> c.type().isContainer())
                       .map(ClusterSpec::id)
                       .collect(Collectors.toUnmodifiableSet());
    }

}
