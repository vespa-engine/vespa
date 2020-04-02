// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.Cluster;
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
    private static final int SPARE_CAPACITY_PROD = 0;
    private static final int SPARE_CAPACITY_NONPROD = 0;

    private final NodeRepository nodeRepository;
    private final CapacityPolicies capacityPolicies;
    private final Zone zone;
    private final Preparer preparer;
    private final Activator activator;
    private final Optional<LoadBalancerProvisioner> loadBalancerProvisioner;

    int getSpareCapacityProd() {
        return SPARE_CAPACITY_PROD;
    }

    @Inject
    public NodeRepositoryProvisioner(NodeRepository nodeRepository, Zone zone,
                                     ProvisionServiceProvider provisionServiceProvider, FlagSource flagSource) {
        this.nodeRepository = nodeRepository;
        this.capacityPolicies = new CapacityPolicies(zone);
        this.zone = zone;
        this.loadBalancerProvisioner = provisionServiceProvider.getLoadBalancerService().map(lbService -> new LoadBalancerProvisioner(nodeRepository, lbService));
        this.preparer = new Preparer(nodeRepository,
                                     zone.environment() == Environment.prod ? SPARE_CAPACITY_PROD : SPARE_CAPACITY_NONPROD,
                                     provisionServiceProvider.getHostProvisioner(),
                                     provisionServiceProvider.getHostResourcesCalculator(),
                                     flagSource,
                                     loadBalancerProvisioner);
        this.activator = new Activator(nodeRepository, loadBalancerProvisioner);
    }


    /**
     * Returns a list of nodes in the prepared or active state, matching the given constraints.
     * The nodes are ordered by increasing index number.
     */
    @Deprecated // TODO: Remove after April 2020
    @Override
    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, Capacity requestedCapacity,
                                  int wantedGroups, ProvisionLogger logger) {
        return prepare(application, cluster, requestedCapacity.withGroups(wantedGroups), logger);
    }

    /**
     * Returns a list of nodes in the prepared or active state, matching the given constraints.
     * The nodes are ordered by increasing index number.
     */
    @Override
    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, Capacity requested,
                                  ProvisionLogger logger) {
        log.log(zone.system().isCd() ? Level.INFO : LogLevel.DEBUG,
                () -> "Received deploy prepare request for " + requested +
                      " for application " + application + ", cluster " + cluster);

        if (cluster.group().isPresent()) throw new IllegalArgumentException("Node requests cannot specify a group");

        if ( ! hasQuota(application, requested.maxResources().nodes()))
            throw new IllegalArgumentException(requested + " requested for " + cluster +
                                               ". Max value exceeds your quota. Resolve this at https://cloud.vespa.ai/quota");

        int groups;
        NodeResources resources;
        NodeSpec nodeSpec;
        if ( requested.type() == NodeType.tenant) {
            ClusterResources target = decideTargetResources(application, cluster.id(), requested);
            int nodeCount = capacityPolicies.decideSize(target.nodes(), requested, cluster, application);
            resources = capacityPolicies.decideNodeResources(target.nodeResources(), requested, cluster);
            boolean exclusive = capacityPolicies.decideExclusivity(cluster.isExclusive());
            groups = Math.min(target.groups(), nodeCount); // cannot have more groups than nodes
            nodeSpec = NodeSpec.from(nodeCount, resources, exclusive, requested.canFail());
            logIfDownscaled(target.nodes(), nodeCount, cluster, logger);
        }
        else {
            groups = 1; // type request with multiple groups is not supported
            resources = requested.minResources().nodeResources();
            nodeSpec = NodeSpec.from(requested.type());
        }
        return asSortedHosts(preparer.prepare(application, cluster, nodeSpec, groups), resources);
    }

    @Override
    public void activate(NestedTransaction transaction, ApplicationId application, Collection<HostSpec> hosts) {
        validate(hosts);
        activator.activate(application, hosts, transaction);
    }

    @Override
    public void restart(ApplicationId application, HostFilter filter) {
        nodeRepository.restart(ApplicationFilter.from(application, NodeHostFilter.from(filter)));
    }

    @Override
    public void remove(NestedTransaction transaction, ApplicationId application) {
        nodeRepository.deactivate(application, transaction);
        loadBalancerProvisioner.ifPresent(lbProvisioner -> lbProvisioner.deactivate(application, transaction));
    }

    /**
     * Returns the target cluster resources, a value between the min and max in the requested capacity,
     * and updates the application store with the received min and max,
     */
    private ClusterResources decideTargetResources(ApplicationId applicationId, ClusterSpec.Id clusterId, Capacity requested) {
        try (Mutex lock = nodeRepository.lock(applicationId)) {
            Application application = nodeRepository.applications().get(applicationId, true);
            application = application.withClusterLimits(clusterId, requested.minResources(), requested.maxResources());
            nodeRepository.applications().set(applicationId, application, lock);
            return application.cluster(clusterId).targetResources()
                    .orElseGet(() -> currentResources(applicationId, clusterId, requested)
                    .orElse(requested.minResources()));
        }
    }

    /** Returns the current resources of this cluster, if it's already deployed and inside the requested limits */
    private Optional<ClusterResources> currentResources(ApplicationId applicationId,
                                                        ClusterSpec.Id clusterId,
                                                        Capacity requested) {
        List<Node> nodes = NodeList.copyOf(nodeRepository.getNodes(applicationId, Node.State.active))
                                   .cluster(clusterId).not().retired().asList();
        if (nodes.size() < 1) return Optional.empty();
        long groups = nodes.stream().map(node -> node.allocation().get().membership().cluster().group()).distinct().count();
        var resources = new ClusterResources(nodes.size(), (int)groups, nodes.get(0).flavor().resources());
        if ( ! resources.isWithin(requested.minResources(), requested.maxResources())) return Optional.empty();
        return Optional.of(resources);
    }

    private void logIfDownscaled(int targetNodes, int actualNodes, ClusterSpec cluster, ProvisionLogger logger) {
        if (zone.environment().isManuallyDeployed() && actualNodes < targetNodes)
            logger.log(Level.INFO, "Requested " + targetNodes + " nodes for " + cluster +
                                   ", downscaling to " + actualNodes + " nodes in " + zone.environment());
    }

    private boolean hasQuota(ApplicationId application, int requestedNodes) {
        if ( ! this.zone.system().isPublic()) return true; // no quota management

        if (application.tenant().value().hashCode() == 3857)        return requestedNodes <= 60;
        if (application.tenant().value().hashCode() == -1271827001) return requestedNodes <= 75;
        return requestedNodes <= 5;
    }

    private List<HostSpec> asSortedHosts(List<Node> nodes, NodeResources requestedResources) {
        nodes.sort(Comparator.comparingInt(node -> node.allocation().get().membership().index()));
        List<HostSpec> hosts = new ArrayList<>(nodes.size());
        for (Node node : nodes) {
            log.log(LogLevel.DEBUG, () -> "Prepared node " + node.hostname() + " - " + node.flavor());
            Allocation nodeAllocation = node.allocation().orElseThrow(IllegalStateException::new);
            hosts.add(new HostSpec(node.hostname(),
                                   List.of(),
                                   Optional.of(node.flavor()),
                                   Optional.of(nodeAllocation.membership()),
                                   node.status().vespaVersion(),
                                   nodeAllocation.networkPorts(),
                                   requestedResources == NodeResources.unspecified ? Optional.empty() : Optional.of(requestedResources),
                                   node.status().dockerImage().map(DockerImage::repository)));
            if (nodeAllocation.networkPorts().isPresent()) {
                log.log(LogLevel.DEBUG, () -> "Prepared node " + node.hostname() + " has port allocations");
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

}
