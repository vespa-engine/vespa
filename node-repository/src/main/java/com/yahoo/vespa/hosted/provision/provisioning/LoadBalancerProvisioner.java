// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.exception.LoadBalancerServiceException;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Dimension;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.IntFlag;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer.State;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerId;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerInstance;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerService;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerSpec;
import com.yahoo.vespa.hosted.provision.lb.Real;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDb;

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

import static com.yahoo.vespa.hosted.provision.lb.LoadBalancerSpec.preProvisionOwner;
import static com.yahoo.vespa.hosted.provision.lb.LoadBalancerSpec.preProvisionSpec;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;

/**
 * Provisions and configures application load balancers.
 *
 * @author mpolden
 */
// Load balancer state transitions:
// 1) (new) -> reserved -> active
// 2) active | reserved -> inactive
// 3) inactive -> active | (removed)
// 4) active | reserved | inactive -> removable
// 5) removable -> (removed)
public class LoadBalancerProvisioner {

    private static final Logger log = Logger.getLogger(LoadBalancerProvisioner.class.getName());

    private final NodeRepository nodeRepository;
    private final CuratorDb db;
    private final LoadBalancerService service;
    private final BooleanFlag deactivateRouting;
    private final BooleanFlag ipv6AwsTargetGroups;
    private final IntFlag preProvisionPoolSize;

    public LoadBalancerProvisioner(NodeRepository nodeRepository, LoadBalancerService service) {
        this.nodeRepository = nodeRepository;
        this.db = nodeRepository.database();
        this.service = service;
        this.deactivateRouting = PermanentFlags.DEACTIVATE_ROUTING.bindTo(nodeRepository.flagSource());
        this.ipv6AwsTargetGroups = Flags.IPV6_AWS_TARGET_GROUPS.bindTo(nodeRepository.flagSource());
        this.preProvisionPoolSize = PermanentFlags.PRE_PROVISIONED_LB_COUNT.bindTo(nodeRepository.flagSource());

        // Read and write all load balancers to make sure they are stored in the latest version of the serialization format
        for (var id : db.readLoadBalancerIds()) {
            try (var lock = db.lock(id.application())) {
                var loadBalancer = db.readLoadBalancer(id);
                loadBalancer.ifPresent(lb -> db.writeLoadBalancer(lb, lb.state()));
            }
        }
    }

    /**
     * Prepare a load balancer for given application and cluster.
     * <p>
     * If a load balancer for the cluster already exists, it will be reconfigured based on the currently allocated
     * nodes. Its state will remain unchanged.
     * <p>
     * If no load balancer exists, a new one will be provisioned in {@link LoadBalancer.State#reserved}.
     * <p>
     * Calling this for irrelevant node or cluster types is a no-op.
     */
    public void prepare(ApplicationId application, ClusterSpec cluster, NodeSpec requested) {
        if (!shouldProvision(application, requested.type(), cluster.type())) return;
        try (var lock = db.lock(application)) {
            ClusterSpec.Id clusterId = effectiveId(cluster);
            LoadBalancerId loadBalancerId = requireNonClashing(new LoadBalancerId(application, clusterId));
            prepare(loadBalancerId, cluster.zoneEndpoint(), requested);
        }
    }

    /**
     * Activate load balancer for given application and cluster.
     * <p>
     * If a load balancer for the cluster already exists, it will be reconfigured based on the currently allocated
     * nodes and the load balancer itself will be moved to {@link LoadBalancer.State#active}.
     * <p>
     * Load balancers for clusters that are no longer in given clusters are deactivated.
     * <p>
     * Calling this when no load balancer has been prepared for given cluster is a no-op.
     */
    public void activate(Set<ClusterSpec> clusters, NodeList newActive, ApplicationTransaction transaction) {
        Map<ClusterSpec.Id, ZoneEndpoint> activatingClusters = clusters.stream()
                                                                       // .collect(Collectors.toMap(ClusterSpec::id, ClusterSpec::zoneEndpoint));
                                                                       // TODO: this dies with combined clusters
                                                                       .collect(groupingBy(LoadBalancerProvisioner::effectiveId,
                                                                                          reducing(ZoneEndpoint.defaultEndpoint,
                                                                                                   ClusterSpec::zoneEndpoint,
                                                                                                   (o, n) -> o.isDefault() ? n : o)));
        for (var cluster : loadBalancedClustersOf(newActive).entrySet()) {
            if ( ! activatingClusters.containsKey(cluster.getKey()))
                continue;

            Node clusterNode = cluster.getValue().first().get();
            if ( ! shouldProvision(transaction.application(), clusterNode.type(), clusterNode.allocation().get().membership().cluster().type())) continue;
            activate(transaction, cluster.getKey(), activatingClusters.get(cluster.getKey()), cluster.getValue());
        }
        // Deactivate any surplus load balancers, i.e. load balancers for clusters that have been removed
        deactivate(surplusLoadBalancersOf(transaction.application(), activatingClusters.keySet()), transaction.nested());
    }

    /**
     * Deactivate all load balancers assigned to given application. This is a no-op if an application does not have any
     * load balancer(s).
     */
    public void deactivate(ApplicationTransaction transaction) {
        deactivate(nodeRepository.loadBalancers().list(transaction.application()).asList(), transaction.nested());
    }

    /** Returns whether to provision a load balancer for given application */
    private boolean shouldProvision(ApplicationId application, NodeType nodeType, ClusterSpec.Type clusterType) {
        if (application.instance().isTester()) return false;  // Do not provision for tester instances
        if (!service.supports(nodeType, clusterType)) return false;  // Nothing to provision for this node and cluster type
        return true;
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
                                                    .toList();
        db.writeLoadBalancers(deactivatedLoadBalancers, LoadBalancer.State.active, transaction);
    }

    /** Find all load balancer IDs owned by given tenant and application */
    private List<LoadBalancerId> findLoadBalancers(TenantName tenant, ApplicationName application) {
        return db.readLoadBalancerIds().stream()
                 .filter(id -> id.application().tenant().equals(tenant) &&
                               id.application().application().equals(application))
                 .toList();
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

    private void prepare(LoadBalancerId id, ZoneEndpoint zoneEndpoint, NodeSpec requested) {
        CloudAccount cloudAccount = requested.cloudAccount();
        Optional<LoadBalancer> loadBalancer = db.readLoadBalancer(id);
        LoadBalancer newLoadBalancer = null;
        LoadBalancer.State fromState = loadBalancer.map(LoadBalancer::state).orElse(null);
        try {
            if (loadBalancer.isPresent() && ! inAccount(cloudAccount, loadBalancer.get())) {
                newLoadBalancer = loadBalancer.get().with(State.removable, nodeRepository.clock().instant());
                throw new LoadBalancerServiceException("Could not (re)configure " + id + " due to change in cloud account. The operation will be retried on next deployment");
            }
            if (loadBalancer.isPresent() && ! hasCorrectVisibility(loadBalancer.get(), zoneEndpoint)) {
                newLoadBalancer = loadBalancer.get().with(State.removable, nodeRepository.clock().instant());
                throw new LoadBalancerServiceException("Could not (re)configure " + id + " due to change in load balancer visibility. The operation will be retried on next deployment");
            }
            newLoadBalancer = loadBalancer.orElseGet(() -> createNewLoadBalancer(id, zoneEndpoint, requested));      // Determine id-seed.
            newLoadBalancer = newLoadBalancer.with(provisionInstance(newLoadBalancer, zoneEndpoint, requested)); // Update instance.
        } catch (LoadBalancerServiceException e) {
            log.log(Level.WARNING, "Failed to provision load balancer", e);
            throw e;
        } finally {
            db.writeLoadBalancer(newLoadBalancer, fromState);
        }
    }

    private static boolean hasCorrectVisibility(LoadBalancer newLoadBalancer, ZoneEndpoint zoneEndpoint) {
        return newLoadBalancer.instance().isEmpty() ||
               newLoadBalancer.instance().get().settings().isPublicEndpoint() == zoneEndpoint.isPublicEndpoint();
    }

    /** Creates a new load balancer, with an instance if one is taken from the pool, or without otherwise. */
    private LoadBalancer createNewLoadBalancer(LoadBalancerId id, ZoneEndpoint zoneEndpoint, NodeSpec requested) {
        LoadBalancerSpec spec = new LoadBalancerSpec(id.application(), id.cluster(), Set.of(), zoneEndpoint,
                                                     requested.cloudAccount(), toSeed(id, requested.type()));
        return provisionFromPool(spec, requested.type())
                .orElseGet(() -> new LoadBalancer(id, spec.idSeed(), Optional.empty(), State.reserved, nodeRepository.clock().instant()));
    }

    /** Provision a load balancer instance, if necessary */
    private LoadBalancerInstance provisionInstance(LoadBalancer currentLoadBalancer,
                                                   ZoneEndpoint zoneEndpoint,
                                                   NodeSpec requested) {
        LoadBalancerId id = currentLoadBalancer.id();
        Set<Real> reals = currentLoadBalancer.instance()
                                             .map(LoadBalancerInstance::reals)
                                             .orElse(Set.of()); // Targeted reals are changed on activation.
        ZoneEndpoint settings = new ZoneEndpoint(zoneEndpoint.isPublicEndpoint(),
                                                 zoneEndpoint.isPrivateEndpoint(),
                                                 currentLoadBalancer.instance()
                                                                    .map(LoadBalancerInstance::settings)
                                                                    .map(ZoneEndpoint::allowedUrns)
                                                                    .orElse(List.of())); // Allowed URNs are changed on activation.
        if (currentLoadBalancer.instance().map(instance -> settings.equals(instance.settings())).orElse(false))
            return currentLoadBalancer.instance().get();

        log.log(Level.INFO, () -> "Provisioning instance for " + id);
        try {
            return service.provision(new LoadBalancerSpec(id.application(), id.cluster(), reals, settings, requested.cloudAccount(), currentLoadBalancer.idSeed()))
                          // Provisioning a private endpoint service requires hard resources to be ready, so we delay it until activation.
                          .withServiceIds(currentLoadBalancer.instance().map(LoadBalancerInstance::serviceIds).orElse(List.of()));
        }
        catch (Exception e) {
            throw new LoadBalancerServiceException("Could not provision " + id + ". The operation will be retried on next deployment.", e);
        }
    }

    private Optional<LoadBalancer> provisionFromPool(LoadBalancerSpec spec, NodeType type) {
        if (type != NodeType.tenant) return Optional.empty();
        if ( ! spec.settings().isDefault()) return Optional.empty();
        if (preProvisionPoolSize.value() == 0) return Optional.empty();

        try (Lock lock = db.lock(preProvisionOwner)) {
            long tail = db.readLoadBalancerPoolTail();
            if (tail >= db.readLoadBalancerPoolHead()) return Optional.empty();
            ClusterSpec.Id slot = slotId(tail);
            Optional<LoadBalancer> candidate = db.readLoadBalancer(new LoadBalancerId(preProvisionOwner, slot));
            if (candidate.flatMap(LoadBalancer::instance).map(instance -> ! instance.cloudAccount().equals(spec.cloudAccount())).orElse(false)) return Optional.empty();
            db.incrementLoadBalancerPoolTail(); // Acquire now; if we fail below, no one else will use the possibly inconsistent instance.
            LoadBalancer chosen = candidate.orElseThrow(() -> new IllegalStateException("could not find load balancer " + slot + " in pre-provisioned pool"));
            if (chosen.state() != State.active || chosen.instance().isEmpty())
                throw new IllegalStateException("expected active load balancer in pre-provisioned pool, but got " + chosen);
            log.log(Level.INFO, "Using " + chosen + " from pre-provisioned pool");
            service.reallocate(new LoadBalancerSpec(spec.application(), spec.cluster(), spec.reals(), spec.settings(), spec.cloudAccount(), chosen.idSeed()));
            db.removeLoadBalancer(chosen.id()); // Using a transaction to remove this, and write the instance, would be better, but much hassle.
            // Should be immediately written again outside of this!
            return Optional.of(new LoadBalancer(new LoadBalancerId(spec.application(), spec.cluster()),
                                                chosen.idSeed(),
                                                chosen.instance(),
                                                State.reserved,
                                                nodeRepository.clock().instant()));
        }
        catch (Exception e) {
            log.log(Level.WARNING, "Failed to provision load balancer from pool", e);
        }
        return Optional.empty();
    }

    static ClusterSpec.Id slotId(long counter) {
        return ClusterSpec.Id.from(String.valueOf(counter));
    }

    static long slotOf(ClusterSpec.Id id) {
        return Long.parseLong(id.value());
    }

    /** Evict surplus and failed load balancers, and pre-provision deficit ones. Should only be run by a maintenance job. */
    public void refreshPool() {
        int size = preProvisionPoolSize.value();
        long head = db.readLoadBalancerPoolHead();
        long tail = db.readLoadBalancerPoolTail();
        try (Lock lock = db.lock(preProvisionOwner)) {
            while (head - tail > size) tail = db.incrementLoadBalancerPoolTail();
            // Mark surplus load balancers, and ones we failed to move to an application, for removal.
            for (LoadBalancer lb : db.readLoadBalancers(l -> l.application().equals(preProvisionOwner)).values()) {
                long slot = slotOf(lb.id().cluster());
                if (slot < tail) db.writeLoadBalancer(lb.with(State.removable, nodeRepository.clock().instant()), lb.state());
            }
        }
        // No need for lock while we provision, since we'll write atomically only after we're done, and the job lock ensures single writer.
        while (head - tail < size) {
            ClusterSpec.Id slot = slotId(head);
            LoadBalancerId id = new LoadBalancerId(preProvisionOwner, slot);
            LoadBalancerSpec spec = preProvisionSpec(slot, nodeRepository.zone().cloud().account(), toSeed(id));
            db.writeLoadBalancer(new LoadBalancer(id,
                                                  spec.idSeed(),
                                                  Optional.of(service.provision(spec)),
                                                  State.active, // Keep the expirer away.
                                                  nodeRepository.clock().instant()),
                                 null);
            head = db.incrementLoadBalancerPoolHead();
        }
    }

    public static String toSeed(LoadBalancerId id, NodeType type) {
        return type == NodeType.tenant ? toSeed(id) : toLegacySeed(id.application(), id.cluster());
    }

    public static String toSeed(LoadBalancerId id) {
        return ":" + id.serializedForm() + ":"; // ಠ_ಠ
    }

    public static String toLegacySeed(ApplicationId application, ClusterSpec.Id cluster) {
        return application.tenant().value() +
               application.application().value() +
               application.instance().value() +
               cluster.value(); // ಠ_ಠ
    }

    private void activate(ApplicationTransaction transaction, ClusterSpec.Id cluster, ZoneEndpoint settings, NodeList nodes) {
        Instant now = nodeRepository.clock().instant();
        LoadBalancerId id = new LoadBalancerId(transaction.application(), cluster);
        Optional<LoadBalancer> loadBalancer = db.readLoadBalancer(id);
        if (loadBalancer.isEmpty()) throw new IllegalArgumentException("Could not activate load balancer that was never prepared: " + id);
        if (loadBalancer.get().instance().isEmpty()) throw new IllegalArgumentException("Activating " + id + ", but prepare never provisioned a load balancer instance");

        try {
            LoadBalancerInstance instance = configureInstance(id, nodes, loadBalancer.get(), settings, loadBalancer.get().instance().get().cloudAccount());
            db.writeLoadBalancers(List.of(loadBalancer.get().with(instance).with(State.active, now)),
                                  loadBalancer.get().state(), transaction.nested());
        } catch (LoadBalancerServiceException e) {
            db.writeLoadBalancers(List.of(loadBalancer.get()), loadBalancer.get().state(), transaction.nested());
            throw e;
        }
    }

    /** Reconfigure a load balancer instance, if necessary */
    private LoadBalancerInstance configureInstance(LoadBalancerId id, NodeList nodes,
                                                   LoadBalancer currentLoadBalancer,
                                                   ZoneEndpoint zoneEndpoint,
                                                   CloudAccount cloudAccount) {
        boolean shouldDeactivateRouting = deactivateRouting.with(Dimension.INSTANCE_ID,
                                                                 id.application().serializedForm())
                                                           .value();
        Set<Real> reals = shouldDeactivateRouting ? Set.of() : realsOf(nodes, cloudAccount);
        log.log(Level.FINE, () -> "Configuring instance for " + id + ", targeting: " + reals);
        try {
            return service.configure(currentLoadBalancer.instance().orElseThrow(() -> new IllegalArgumentException("expected existing instance for " + id)),
                                     new LoadBalancerSpec(id.application(), id.cluster(), reals, zoneEndpoint, cloudAccount, currentLoadBalancer.idSeed()),
                                     shouldDeactivateRouting || currentLoadBalancer.state() != LoadBalancer.State.active);
        }
        catch (Exception e) {
            throw new LoadBalancerServiceException("Could not (re)configure " + id + ", targeting: " + reals, e);
        }
    }

    /** Returns the load balanced clusters of given application and their nodes */
    private Map<ClusterSpec.Id, NodeList> loadBalancedClustersOf(NodeList nodes) {
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
    private Set<Real> realsOf(NodeList nodes, CloudAccount cloudAccount) {
        Set<Real> reals = new LinkedHashSet<>();
        for (var node : nodes) {
            for (var ip : reachableIpAddresses(node, cloudAccount)) {
                reals.add(new Real(HostName.of(node.hostname()), ip));
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

    /** Returns whether load balancer is provisioned in given account */
    private boolean inAccount(CloudAccount cloudAccount, LoadBalancer loadBalancer) {
        return !nodeRepository.zone().cloud().allowEnclave() || loadBalancer.instance().isEmpty() || loadBalancer.instance().get().cloudAccount().equals(cloudAccount);
    }

    /** Find IP addresses reachable by the load balancer service */
    private Set<String> reachableIpAddresses(Node node, CloudAccount cloudAccount) {
        Set<String> reachable = new LinkedHashSet<>(node.ipConfig().primary());
        boolean forceIpv6 = ipv6AwsTargetGroups.with(Dimension.CLOUD_ACCOUNT, cloudAccount.account()).value();
        var protocol = forceIpv6 ? LoadBalancerService.Protocol.ipv6 :
                service.protocol(node.cloudAccount().isExclave(nodeRepository.zone()));
        // Remove addresses unreachable by the load balancer service
        switch (protocol) {
            case ipv4 -> reachable.removeIf(IP::isV6);
            case ipv6 -> reachable.removeIf(IP::isV4);
        }
        return reachable;
    }

    private static ClusterSpec.Id effectiveId(ClusterSpec cluster) {
        return cluster.combinedId().orElse(cluster.id());
    }

}
