// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.google.inject.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.ProvisionLogger;
import com.yahoo.config.provision.Provisioner;
import com.yahoo.config.provision.Zone;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
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
    @Override
    public List<HostSpec> prepare(ApplicationId application, ClusterSpec cluster, Capacity requestedCapacity, 
                                  int wantedGroups, ProvisionLogger logger) {
        if (cluster.group().isPresent()) throw new IllegalArgumentException("Node requests cannot specify a group");
        if (requestedCapacity.nodeCount() > 0 && requestedCapacity.nodeCount() % wantedGroups != 0)
            throw new IllegalArgumentException("Requested " + requestedCapacity.nodeCount() + " nodes in " + wantedGroups + " groups, " +
                                               "which doesn't allow the nodes to be divided evenly into groups");

        log.log(zone.system().isCd() ? Level.INFO : LogLevel.DEBUG,
                () -> "Received deploy prepare request for " + requestedCapacity + " in " +
                      wantedGroups + " groups for application " + application + ", cluster " + cluster);

        int effectiveGroups;
        NodeSpec requestedNodes;
        Optional<NodeResources> resources = requestedCapacity.nodeResources();
        if ( requestedCapacity.type() == NodeType.tenant) {
            int nodeCount = capacityPolicies.decideSize(requestedCapacity, cluster.type(), application);
            if (zone.environment().isManuallyDeployed() && nodeCount < requestedCapacity.nodeCount())
                logger.log(Level.INFO, "Requested " + requestedCapacity.nodeCount() + " nodes for " + cluster +
                                       ", downscaling to " + nodeCount + " nodes in " + zone.environment());
            resources = Optional.of(capacityPolicies.decideNodeResources(requestedCapacity, cluster));
            boolean exclusive = capacityPolicies.decideExclusivity(cluster.isExclusive());
            effectiveGroups = Math.min(wantedGroups, nodeCount); // cannot have more groups than nodes
            requestedNodes = NodeSpec.from(nodeCount, resources.get(), exclusive, requestedCapacity.canFail());

            if ( ! hasQuota(application, nodeCount))
                throw new IllegalArgumentException(requestedCapacity + " requested for " + cluster +
                                                   (requestedCapacity.nodeCount() != nodeCount ? " resolved to " + nodeCount + " nodes" : "") +
                                                   " exceeds your quota. Please contact Vespa support.");
        }
        else {
            requestedNodes = NodeSpec.from(requestedCapacity.type());
            effectiveGroups = 1; // type request with multiple groups is not supported
        }

        return asSortedHosts(preparer.prepare(application, cluster, requestedNodes, effectiveGroups), resources);
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

    private boolean hasQuota(ApplicationId application, int requestedNodes) {
        if ( ! this.zone.system().isPublic()) return true; // no quota management

        if (application.tenant().value().hashCode() == 3857) return requestedNodes <= 60;
        return requestedNodes <= 5;
    }

    private List<HostSpec> asSortedHosts(List<Node> nodes, Optional<NodeResources> requestedResources) {
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
                                   requestedResources));
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
