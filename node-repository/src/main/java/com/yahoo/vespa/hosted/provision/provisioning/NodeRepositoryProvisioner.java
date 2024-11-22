// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationMutex;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.CapacityPolicies;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
import com.yahoo.vespa.hosted.provision.autoscale.AllocatableResources;
import com.yahoo.vespa.hosted.provision.autoscale.AllocationOptimizer;
import com.yahoo.vespa.hosted.provision.autoscale.ClusterModel;
import com.yahoo.vespa.hosted.provision.autoscale.Limits;
import com.yahoo.vespa.hosted.provision.autoscale.Load;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.filter.ApplicationFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeHostFilter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of the host provisioner API for hosted Vespa, using the node repository to allocate nodes.
 * Does not allocate hosts for the routing application, see VespaModelFactory.createHostProvisioner
 *
 * @author bratseth
 */
public class NodeRepositoryProvisioner implements Provisioner {

    private static final Logger log = Logger.getLogger(NodeRepositoryProvisioner.class.getName());

    private final NodeRepository nodeRepository;
    private final AllocationOptimizer allocationOptimizer;
    private final Zone zone;
    private final Preparer preparer;
    private final Activator activator;
    private final Optional<LoadBalancerProvisioner> loadBalancerProvisioner;

    @Inject
    public NodeRepositoryProvisioner(NodeRepository nodeRepository,
                                     Zone zone,
                                     ProvisionServiceProvider provisionServiceProvider,
                                     Metric metric) {
        this.nodeRepository = nodeRepository;
        this.allocationOptimizer = new AllocationOptimizer(nodeRepository);
        this.zone = zone;
        this.loadBalancerProvisioner = provisionServiceProvider.getLoadBalancerService()
                                                               .map(lbService -> new LoadBalancerProvisioner(nodeRepository, lbService));
        this.preparer = new Preparer(nodeRepository,
                                     provisionServiceProvider.getHostProvisioner(),
                                     loadBalancerProvisioner,
                                     metric);
        this.activator = new Activator(nodeRepository, loadBalancerProvisioner);
    }


    /**
     * Returns a list of nodes in the prepared or active state, matching the given constraints.
     * The nodes are ordered by increasing index number.
     */
    @Override
    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, Capacity requested, ProvisionLogger logger) {
        log.log(Level.FINE, "Received deploy prepare request for " + requested +
                            " for application " + application + ", cluster " + cluster);
        validate(application, cluster, requested, logger);

        var capacityPolicies = nodeRepository.capacityPoliciesFor(application);
        NodeResources resources;
        NodeSpec nodeSpec;
        if (requested.type() == NodeType.tenant) {
            cluster = capacityPolicies.decideExclusivity(requested, cluster);
            Capacity actual = capacityPolicies.applyOn(requested, cluster.isExclusive());
            ClusterResources target = decideTargetResources(application, cluster, actual, capacityPolicies);
            validate(actual, target, cluster, application);
            logIfDownscaled(requested.minResources().nodes(), actual.minResources().nodes(), cluster, logger);

            resources = getNodeResources(cluster, target.nodeResources(), application, capacityPolicies);
            nodeSpec = NodeSpec.from(target.nodes(), target.groups(), resources, cluster.isExclusive(), actual.canFail(),
                                     requested.cloudAccount().orElse(nodeRepository.zone().cloud().account()),
                                     requested.clusterInfo().hostTTL());
        }
        else {
            cluster = cluster.withExclusivity(true);
            resources = getNodeResources(cluster, requested.minResources().nodeResources(), application, capacityPolicies);
            nodeSpec = NodeSpec.from(requested.type(), nodeRepository.zone().cloud().account());
        }
        return asSortedHosts(preparer.prepare(application, cluster, nodeSpec),
                             requireCompatibleResources(resources, cluster));
    }

    private void validate(ApplicationId application, ClusterSpec cluster, Capacity requested, ProvisionLogger logger) {
        if (cluster.group().isPresent()) throw new IllegalArgumentException("Node requests cannot specify a group");

        nodeRepository.nodeResourceLimits().ensureWithinAdvertisedLimits("Min", requested.minResources().nodeResources(), cluster);
        nodeRepository.nodeResourceLimits().ensureWithinAdvertisedLimits("Max", requested.maxResources().nodeResources(), cluster);

        if (!requested.minResources().nodeResources().gpuResources().equals(requested.maxResources().nodeResources().gpuResources()))
            throw new IllegalArgumentException(requested + " is invalid: GPU capacity cannot have ranges");

        logInsufficientDiskResources(cluster, requested, logger);
    }

    private void logInsufficientDiskResources(ClusterSpec cluster, Capacity requested, ProvisionLogger logger) {
        var resources = requested.minResources().nodeResources();
        if ( ! nodeRepository.nodeResourceLimits().isWithinAdvertisedDiskLimits(resources, cluster)) {
            logger.logApplicationPackage(Level.WARNING, "Requested disk (" + resources.diskGb() +
                                                        "Gb) in " + cluster.id() + " is not large enough to fit " +
                                                        "core/heap dumps. Minimum recommended disk resources " +
                                                        "is 2x memory for containers and 3x memory for content");
        }
    }

    private NodeResources getNodeResources(ClusterSpec cluster, NodeResources nodeResources, ApplicationId applicationId, CapacityPolicies capacityPolicies) {
        return capacityPolicies.specifyFully(nodeResources, cluster);
    }

    @Override
    public void activate(Collection<HostSpec> hosts, ActivationContext context, ApplicationTransaction transaction) {
        validate(hosts);
        activator.activate(hosts, context, transaction);
    }

    @Override
    public void restart(ApplicationId application, HostFilter filter) {
        List<Node> updated = nodeRepository.nodes().restartActive(ApplicationFilter.from(application).and(NodeHostFilter.from(filter)));
        if (updated.isEmpty()) {
            throw new IllegalArgumentException("No matching nodes found");
        }
    }

    @Override
    public void remove(ApplicationTransaction transaction) {
        nodeRepository.remove(transaction);
        loadBalancerProvisioner.ifPresent(lbProvisioner -> lbProvisioner.deactivate(transaction));
    }

    @Override
    public ApplicationMutex lock(ApplicationId application) {
        return new ApplicationMutex(application, nodeRepository.applications().lock(application));
    }

    /**
     * Returns the target cluster resources, a value between the min and max in the requested capacity,
     * and updates the application store with the received min and max.
     */
    private ClusterResources decideTargetResources(ApplicationId applicationId, ClusterSpec clusterSpec, Capacity requested,
                                                   CapacityPolicies capacityPolicies) {
        try (Mutex lock = nodeRepository.applications().lock(applicationId)) {
            var application = nodeRepository.applications().get(applicationId).orElse(Application.empty(applicationId))
                              .withCluster(clusterSpec.id(), clusterSpec.isExclusive(), requested);
            nodeRepository.applications().put(application, lock);
            var cluster = application.cluster(clusterSpec.id()).get();
            return cluster.target().resources().orElseGet(() -> currentResources(application, clusterSpec, cluster, requested, capacityPolicies));
        }
    }

    /** Returns the current resources of this cluster, or requested min if none */
    private ClusterResources currentResources(Application application,
                                              ClusterSpec clusterSpec,
                                              Cluster cluster,
                                              Capacity requested,
                                              CapacityPolicies capacityPolicies) {
        NodeList nodes = nodeRepository.nodes().list(Node.State.active).owner(application.id())
                                       .cluster(clusterSpec.id())
                                       .not().retired()
                                       .not().removable();
        boolean firstDeployment = nodes.isEmpty();
        var current =
                firstDeployment // start at min, preserve current resources otherwise
                ? new AllocatableResources(initialResourcesFrom(requested, clusterSpec, application.id(), capacityPolicies), clusterSpec,
                                           nodeRepository, requested.cloudAccount().orElse(CloudAccount.empty))
                : new AllocatableResources(nodes, nodeRepository);
        var model = new ClusterModel(nodeRepository, application, clusterSpec, cluster, nodes, current, nodeRepository.metricsDb(), nodeRepository.clock());
        return within(Limits.of(requested), model, firstDeployment);
    }

    private ClusterResources initialResourcesFrom(Capacity requested, ClusterSpec clusterSpec, ApplicationId applicationId, CapacityPolicies capacityPolicies) {
        return capacityPolicies.specifyFully(requested.minResources(), clusterSpec);
    }


    /** Make the minimal adjustments needed to the current resources to stay within the limits */
    private ClusterResources within(Limits limits, ClusterModel model, boolean firstDeployment) {
        if (limits.min().equals(limits.max())) return limits.min();

        // Don't change current deployments that are still legal
        if (! firstDeployment && model.current().advertisedResources().isWithin(limits.min(), limits.max()))
            return model.current().advertisedResources();

        // Otherwise, find an allocation that preserves the current resources as well as possible
        return allocationOptimizer.findBestAllocation(Load.one(), model, limits, false)
                                  .orElseThrow(() -> newNoAllocationPossible(model.current().clusterSpec(), limits))
                                  .advertisedResources();
    }

    private void validate(Capacity actual, ClusterResources target, ClusterSpec cluster, ApplicationId application) {
        if ( ! actual.canFail()) return;

        if (! application.instance().isTester() && zone.environment().isProduction() &&
            requiresRedundancy(cluster.type()) && target.nodes() == 1)
            throw new IllegalArgumentException("In " + cluster +
                                               ": Deployments to prod require at least 2 nodes per cluster for redundancy.");

        if ( ! actual.groupSize().includes(target.nodes() / target.groups()))
            throw new IllegalArgumentException("In " + cluster + ": Group size with " + target +
                                               " is not within allowed group size " + actual.groupSize());
    }

    private static boolean requiresRedundancy(ClusterSpec.Type clusterType) {
        return clusterType.isContent() || clusterType.isContainer();
    }

    private void logIfDownscaled(int requestedMinNodes, int actualMinNodes, ClusterSpec cluster, ProvisionLogger logger) {
        if (zone.environment().isManuallyDeployed() && actualMinNodes < requestedMinNodes)
            logger.log(Level.INFO, "Requested " + requestedMinNodes + " nodes for " + cluster +
                                   ", downscaling to " + actualMinNodes + " nodes in " + zone.environment());
    }

    private List<HostSpec> asSortedHosts(List<Node> nodes, NodeResources requestedResources) {
        nodes = new ArrayList<>(nodes);
        nodes.sort(Comparator.comparingInt(node -> node.allocation().get().membership().index()));
        List<HostSpec> hosts = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            log.log(Level.FINE, () -> "Prepared node " + node.hostname() + " - " + node.flavor());
            Allocation nodeAllocation = node.allocation().orElseThrow(IllegalStateException::new);
            hosts.add(new HostSpec(node.hostname(),
                                   nodeRepository.resourcesCalculator().realResourcesOf(node, nodeRepository),
                                   node.flavor().resources(),
                                   requestedResources,
                                   nodeAllocation.membership(),
                                   node.status().vespaVersion(),
                                   nodeAllocation.networkPorts(),
                                   node.status().containerImage()));
            if (nodeAllocation.networkPorts().isPresent()) {
                log.log(Level.FINE, () -> "Prepared node " + node.hostname() + " has port allocations");
            }
        }
        return hosts;
    }

    private void validate(Collection<HostSpec> hosts) {
        for (HostSpec host : hosts) {
            if (host.membership().isEmpty())
                throw new IllegalArgumentException("Hosts must be assigned a cluster when activating, but got " + host);
            if (host.membership().get().cluster().group().isEmpty())
                throw new IllegalArgumentException("Hosts must be assigned a group when activating, but got " + host);
        }
    }

    private static NodeResources requireCompatibleResources(NodeResources nodeResources, ClusterSpec cluster) {
        if (cluster.type() != ClusterSpec.Type.container && !nodeResources.gpuResources().isZero()) {
            throw new IllegalArgumentException(cluster.id() + " of type " + cluster.type() + " does not support GPU resources");
        }
        return nodeResources;
    }

    private IllegalArgumentException newNoAllocationPossible(ClusterSpec spec, Limits limits) {
        StringBuilder message = new StringBuilder("No allocation possible within ").append(limits);

        if (nodeRepository.exclusivity().allocation(spec) && findNearestNodeResources(limits).isPresent())
            message.append(". Nearest allowed node resources: ").append(findNearestNodeResources(limits).get());

        return new IllegalArgumentException(message.toString());
    }

    private Optional<NodeResources> findNearestNodeResources(Limits limits) {
        Optional<NodeResources> nearestMin = nearestFlavorResources(limits.min().nodeResources());
        Optional<NodeResources> nearestMax = nearestFlavorResources(limits.max().nodeResources());
        if (nearestMin.isEmpty()) return nearestMax;
        if (nearestMax.isEmpty()) return nearestMin;
        if (limits.min().nodeResources().distanceTo(nearestMin.get()) < limits.max().nodeResources().distanceTo(nearestMax.get()))
            return nearestMin;
        else
            return nearestMax;
    }

    /** Returns the advertised flavor resources which are nearest to the given resources */
    private Optional<NodeResources> nearestFlavorResources(NodeResources requestedResources) {
        return nodeRepository.flavors().getFlavors().stream()
                             .map(flavor -> nodeRepository.resourcesCalculator().advertisedResourcesOf(flavor))
                             .filter(resources -> resources.satisfies(requestedResources))
                             .min(Comparator.comparingDouble(resources -> resources.distanceTo(requestedResources)))
                             .map(resources -> resources.withBandwidthGbps(requestedResources.bandwidthGbps()))
                             .map(resources -> resources.storageType() == NodeResources.StorageType.remote ?
                                               resources.withDiskGb(requestedResources.diskGb()) : resources);
    }

}
