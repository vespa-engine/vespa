// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.ProvisionLock;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.exception.LoadBalancerServiceException;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerSpec;
import com.yahoo.vespa.hosted.provision.lb.Real;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
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

    public LoadBalancerProvisioner(NodeRepository nodeRepository, LoadBalancerService service, FlagSource flagSource) {
        this.nodeRepository = nodeRepository;
        this.db = nodeRepository.database();
        this.service = service;
        // Read and write all load balancers to make sure they are stored in the latest version of the serialization format
        for (var id : db.readLoadBalancerIds()) {
            try (var lock = db.lock(id.application())) {
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
        if (!service.canForwardTo(requestedNodes.type(), cluster.type())) return; // Nothing to provision for this node and cluster type
        if (application.instance().isTester()) return; // Do not provision for tester instances
        try (var lock = db.lock(application)) {
            ClusterSpec.Id clusterId = effectiveId(cluster);
            NodeList nodes = nodesOf(clusterId, application);
            LoadBalancerId loadBalancerId = requireNonClashing(new LoadBalancerId(application, clusterId));
            ApplicationTransaction transaction = new ApplicationTransaction(new ProvisionLock(application, lock), new NestedTransaction());
            provision(transaction, loadBalancerId, nodes, false);
            transaction.nested().commit();
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
    public void activate(Set<ClusterSpec> clusters, ApplicationTransaction transaction) {
        for (var cluster : loadBalancedClustersOf(transaction.application()).entrySet()) {
            // Provision again to ensure that load balancer instance is re-configured with correct nodes
            provision(transaction, cluster.getKey(), cluster.getValue());
        }
        // Deactivate any surplus load balancers, i.e. load balancers for clusters that have been removed
        var surplusLoadBalancers = surplusLoadBalancersOf(transaction.application(), clusters.stream()
                                                                                      .map(LoadBalancerProvisioner::effectiveId)
                                                                                      .collect(Collectors.toSet()));
        deactivate(surplusLoadBalancers, transaction.nested());
    }

    /**
     * Deactivate all load balancers assigned to given application. This is a no-op if an application does not have any
     * load balancer(s).
     */
    public void deactivate(ApplicationTransaction transaction) {
        deactivate(nodeRepository.loadBalancers().list(transaction.application()).asList(), transaction.nested());
    }

    /** Returns load balancers of given application that are no longer referenced by given clusters */
    private List<LoadBalancer> surplusLoadBalancersOf(ApplicationId application, Set<ClusterSpec.Id> activeClusters) {
        var activeLoadBalancersByCluster = nodeRepository.loadBalancers().list(application)
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

    /** Find all load balancer IDs owned by given tenant and application */
    private List<LoadBalancerId> findLoadBalancers(TenantName tenant, ApplicationName application) {
        return db.readLoadBalancerIds().stream()
                 .filter(id -> id.application().tenant().equals(tenant) &&
                               id.application().application().equals(application))
                 .collect(Collectors.toUnmodifiableList());
    }

    /** Require that load balancer IDs do not clash. This prevents name clashing when compacting endpoint DNS names */
    private LoadBalancerId requireNonClashing(LoadBalancerId loadBalancerId) {
        List<LoadBalancerId> loadBalancerIds = findLoadBalancers(loadBalancerId.application().tenant(),
                                                                 loadBalancerId.application().application());
        List<String> nonCompactableIds = withoutCompactableIds(loadBalancerId);
        for (var id : loadBalancerIds) {
            if (id.equals(loadBalancerId)) continue;
            if (nonCompactableIds.equals(withoutCompactableIds(id))) {
                throw new IllegalArgumentException(loadBalancerId + " clashes with " + id);
            }
        }
        return loadBalancerId;
    }

    /** Idempotently provision a load balancer for given application and cluster */
    private void provision(ApplicationTransaction transaction, LoadBalancerId id, NodeList nodes, boolean activate) {
        Instant now = nodeRepository.clock().instant();
        Optional<LoadBalancer> loadBalancer = db.readLoadBalancer(id);
        if (loadBalancer.isEmpty() && activate) return; // Nothing to activate as this load balancer was never prepared
        LoadBalancerInstance instance = provisionInstance(id, realsOf(nodes), loadBalancer);
        LoadBalancer newLoadBalancer;
        if (loadBalancer.isEmpty()) {
            newLoadBalancer = new LoadBalancer(id, instance, LoadBalancer.State.reserved, now);
        } else {
            var newState = activate ? LoadBalancer.State.active : loadBalancer.get().state();
            newLoadBalancer = loadBalancer.get().with(instance).with(newState, now);
            if (loadBalancer.get().state() != newLoadBalancer.state()) {
                log.log(Level.FINE, "Moving " + newLoadBalancer.id() + " to state " + newLoadBalancer.state());
            }
        }
        db.writeLoadBalancers(List.of(newLoadBalancer), transaction.nested());
    }

    private void provision(ApplicationTransaction transaction, ClusterSpec.Id clusterId, NodeList nodes) {
        provision(transaction, new LoadBalancerId(transaction.application(), clusterId), nodes, true);
    }

    /** Provision or reconfigure a load balancer instance, if necessary */
    private LoadBalancerInstance provisionInstance(LoadBalancerId id, Set<Real> reals,
                                                   Optional<LoadBalancer> currentLoadBalancer) {
        if (hasReals(currentLoadBalancer, reals)) return currentLoadBalancer.get().instance();
        log.log(Level.FINE, "Creating " + id + ", targeting: " + reals);
        try {
            return service.create(new LoadBalancerSpec(id.application(), id.cluster(), reals),
                                  allowEmptyReals(currentLoadBalancer));
        } catch (Exception e) {
            throw new LoadBalancerServiceException("Failed to (re)configure " + id + ", targeting: " +
                                                   reals + ". The operation will be retried on next deployment", e);
        }
    }

    /** Returns the nodes allocated to the given load balanced cluster */
    private NodeList nodesOf(ClusterSpec.Id loadBalancedCluster, ApplicationId application) {
        return loadBalancedClustersOf(application).getOrDefault(loadBalancedCluster, NodeList.copyOf(List.of()));
    }

    /** Returns the load balanced clusters of given application and their nodes */
    private Map<ClusterSpec.Id, NodeList> loadBalancedClustersOf(ApplicationId application) {
        NodeList nodes = nodeRepository.nodes().list(Node.State.reserved, Node.State.active).owner(application);
        if (nodes.stream().anyMatch(node -> node.type() == NodeType.config)) {
            nodes = nodes.nodeType(NodeType.config).type(ClusterSpec.Type.admin);
        } else if (nodes.stream().anyMatch(node -> node.type() == NodeType.controller)) {
            nodes = nodes.nodeType(NodeType.controller).container();
        } else {
            nodes = nodes.nodeType(NodeType.tenant).container();
        }
        return nodes.groupingBy(node -> effectiveId(node.allocation().get().membership().cluster()));
    }

    /** Returns real servers for given nodes */
    private Set<Real> realsOf(NodeList nodes) {
        var reals = new LinkedHashSet<Real>();
        for (var node : nodes) {
            for (var ip : reachableIpAddresses(node)) {
                reals.add(new Real(HostName.from(node.hostname()), ip));
            }
        }
        return reals;
    }

    /** Returns a list of the non-compactable IDs of given load balancer */
    private static List<String> withoutCompactableIds(LoadBalancerId id) {
        List<String> ids = new ArrayList<>(2);
        if (!"default".equals(id.cluster().value())) {
            ids.add(id.cluster().value());
        }
        if (!id.application().instance().isDefault()) {
            ids.add(id.application().instance().value());
        }
        return ids;
    }

    /** Returns whether load balancer has given reals */
    private static boolean hasReals(Optional<LoadBalancer> loadBalancer, Set<Real> reals) {
        if (loadBalancer.isEmpty()) return false;
        return loadBalancer.get().instance().reals().equals(reals);
    }

    /** Returns whether to allow given load balancer to have no reals */
    private static boolean allowEmptyReals(Optional<LoadBalancer> loadBalancer) {
        return loadBalancer.isPresent() && loadBalancer.get().state() != LoadBalancer.State.active;
    }

    /** Find IP addresses reachable by the load balancer service */
    private Set<String> reachableIpAddresses(Node node) {
        Set<String> reachable = new LinkedHashSet<>(node.ipConfig().primary());
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

    private static ClusterSpec.Id effectiveId(ClusterSpec cluster) {
        return cluster.combinedId().orElse(cluster.id());
    }


}
