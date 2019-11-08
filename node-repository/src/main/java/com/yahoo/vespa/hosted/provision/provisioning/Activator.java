// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ParentHostUnavailableException;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Allocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Performs activation of resources for an application. E.g. nodes or load balancers.
 *
 * @author bratseth
 */
class Activator {

    private static final Logger logger = Logger.getLogger(InfraDeployerImpl.class.getName());

    private final NodeRepository nodeRepository;
    private final Optional<LoadBalancerProvisioner> loadBalancerProvisioner;

    public Activator(NodeRepository nodeRepository, Optional<LoadBalancerProvisioner> loadBalancerProvisioner) {
        this.nodeRepository = nodeRepository;
        this.loadBalancerProvisioner = loadBalancerProvisioner;
    }

    /** Activate required resources for given application */
    public void activate(ApplicationId application, Collection<HostSpec> hosts, NestedTransaction transaction) {
        try (Mutex lock = nodeRepository.lock(application)) {
            activateNodes(application, hosts, transaction, lock);
            activateLoadBalancers(application, hosts, transaction, lock);
        }
    }

    /**
     * Add operations to activates nodes for an application to the given transaction.
     * The operations are not effective until the transaction is committed.
     * <p>
     * Pre condition: The application has a possibly empty set of nodes in each of reserved and active.
     * <p>
     * Post condition: Nodes in reserved which are present in <code>hosts</code> are moved to active.
     * Nodes in active which are not present in <code>hosts</code> are moved to inactive.
     *
     * @param transaction Transaction with operations to commit together with any operations done within the repository.
     * @param application the application to allocate nodes for
     * @param hosts the hosts to make the set of active nodes of this
     * @param applicationLock application lock that must be held when calling this
     */
    private void activateNodes(ApplicationId application, Collection<HostSpec> hosts, NestedTransaction transaction,
                               @SuppressWarnings("unused") Mutex applicationLock) {
        Set<String> hostnames = hosts.stream().map(HostSpec::hostname).collect(Collectors.toSet());
        NodeList allNodes = nodeRepository.list();
        NodeList applicationNodes = allNodes.owner(application);

        List<Node> reserved = applicationNodes.state(Node.State.reserved).asList();
        List<Node> reservedToActivate = retainHostsInList(hostnames, reserved);
        List<Node> active = applicationNodes.state(Node.State.active).asList();
        List<Node> continuedActive = retainHostsInList(hostnames, active);
        List<Node> allActive = new ArrayList<>(continuedActive);
        allActive.addAll(reservedToActivate);
        if (!containsAll(hostnames, allActive))
            throw new IllegalArgumentException("Activation of " + application + " failed. " +
                                               "Could not find all requested hosts." +
                                               "\nRequested: " + hosts +
                                               "\nReserved: " + toHostNames(reserved) +
                                               "\nActive: " + toHostNames(active) +
                                               "\nThis might happen if the time from reserving host to activation takes " +
                                               "longer time than reservation expiry (the hosts will then no longer be reserved)");

        validateParentHosts(application, allNodes, reservedToActivate);

        List<Node> activeToRemove = removeHostsFromList(hostnames, active);
        activeToRemove = activeToRemove.stream().map(Node::unretire).collect(Collectors.toList()); // only active nodes can be retired
        nodeRepository.deactivate(activeToRemove, transaction);
        nodeRepository.activate(updateFrom(hosts, continuedActive), transaction); // update active with any changes
        nodeRepository.activate(updatePortsFrom(hosts, reservedToActivate), transaction);
    }

    /** Activate load balancers */
    private void activateLoadBalancers(ApplicationId application, Collection<HostSpec> hosts, NestedTransaction transaction,
                                       @SuppressWarnings("unused") Mutex applicationLock) {
        loadBalancerProvisioner.ifPresent(provisioner -> provisioner.activate(application, clustersOf(hosts), applicationLock, transaction));
    }

    private static Set<ClusterSpec> clustersOf(Collection<HostSpec> hosts) {
        return hosts.stream()
                    .map(HostSpec::membership)
                    .flatMap(Optional::stream)
                    .map(ClusterMembership::cluster)
                    .collect(Collectors.toUnmodifiableSet());
    }

    private static void validateParentHosts(ApplicationId application, NodeList nodes, List<Node> potentialChildren) {
        Set<String> parentHostnames = potentialChildren.stream()
                .map(Node::parentHostname)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        Set<String> nonActiveHosts = nodes.asList().stream()
                .filter(node -> parentHostnames.contains(node.hostname()))
                .filter(node -> node.state() != Node.State.active)
                .map(Node::hostname)
                .collect(Collectors.toSet());
        long numNonActive = nonActiveHosts.size();
        if (numNonActive > 0) {
            // Note: log parent hosts not ready, but do not add to exception (to avoid leaking hostnames)
            logger.log(LogLevel.INFO, application + ": Parent hosts not ready: " + nonActiveHosts);
            throw new ParentHostUnavailableException("Waiting for hosts to finish booting: " +
                    numNonActive + "/" + parentHostnames.size() + " left.");
        }
    }

    private List<Node> retainHostsInList(Set<String> hosts, List<Node> nodes) {
        return nodes.stream().filter(node -> hosts.contains(node.hostname())).collect(Collectors.toList());
    }

    private List<Node> removeHostsFromList(Set<String> hosts, List<Node> nodes) {
        return nodes.stream().filter(node ->  ! hosts.contains(node.hostname())).collect(Collectors.toList());
    }

    private Set<String> toHostNames(List<Node> nodes) {
        return nodes.stream().map(Node::hostname).collect(Collectors.toSet());
    }

    private boolean containsAll(Set<String> hosts, List<Node> nodes) {
        Set<String> notFoundHosts = new HashSet<>(hosts);
        for (Node node : nodes)
            notFoundHosts.remove(node.hostname());
        return notFoundHosts.isEmpty();
    }

    /** Returns the input nodes with the changes resulting from applying the settings in hosts to the given list of nodes. */
    private List<Node> updateFrom(Collection<HostSpec> hosts, List<Node> nodes) {
        List<Node> updated = new ArrayList<>();
        for (Node node : nodes) {
            HostSpec hostSpec = getHost(node.hostname(), hosts);
            node = hostSpec.membership().get().retired() ? node.retire(nodeRepository.clock().instant()) : node.unretire();
            if (hostSpec.flavor().isPresent()) // Docker nodes may change flavor
                node = node.with(hostSpec.flavor().get());
            Allocation allocation = node.allocation().get()
                                        .with(hostSpec.membership().get())
                                        .withRequestedResources(hostSpec.requestedResources()
                                                                        .orElse(node.flavor().resources()));
            if (hostSpec.networkPorts().isPresent())
                allocation = allocation.withNetworkPorts(hostSpec.networkPorts().get());
            node = node.with(allocation);
            updated.add(node);
        }
        return updated;
    }

    /**
     * Returns the input nodes with any port allocations from the hosts
     */
    private List<Node> updatePortsFrom(Collection<HostSpec> hosts, List<Node> nodes) {
        List<Node> updated = new ArrayList<>();
        for (Node node : nodes) {
            HostSpec hostSpec = getHost(node.hostname(), hosts);
            Allocation allocation = node.allocation().get();
            if (hostSpec.networkPorts().isPresent()) {
                allocation = allocation.withNetworkPorts(hostSpec.networkPorts().get());
                node = node.with(allocation);
            }
            updated.add(node);
        }
        return updated;
    }

    private HostSpec getHost(String hostname, Collection<HostSpec> fromHosts) {
        for (HostSpec host : fromHosts)
            if (host.hostname().equals(hostname))
                return host;
        return null;
    }

}
