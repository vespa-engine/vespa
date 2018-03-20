// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.transaction.Mutex;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Performs activation of nodes for an application
 *
 * @author bratseth
 */
class Activator {

    private final NodeRepository nodeRepository;

    public Activator(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
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
     */
    public void activate(ApplicationId application, Collection<HostSpec> hosts, NestedTransaction transaction) {
        try (Mutex lock = nodeRepository.lock(application)) {
            Set<String> hostnames = hosts.stream().map(HostSpec::hostname).collect(Collectors.toSet());

            List<Node> reserved = nodeRepository.getNodes(application, Node.State.reserved);
            List<Node> reservedToActivate = retainHostsInList(hostnames, reserved);
            List<Node> active = nodeRepository.getNodes(application, Node.State.active);
            List<Node> continuedActive = retainHostsInList(hostnames, active);
            List<Node> allActive = new ArrayList<>(continuedActive);
            allActive.addAll(reservedToActivate);
            if ( ! containsAll(hostnames, allActive))
                throw new IllegalArgumentException("Activation of " + application + " failed. " +
                                                   "Could not find all requested hosts." +
                                                   "\nRequested: " + hosts +
                                                   "\nReserved: " + toHostNames(reserved) +
                                                   "\nActive: " + toHostNames(active) +
                                                   "\nThis might happen if the time from reserving host to activation takes " +
                                                   "longer time than reservation expiry (the hosts will then no longer be reserved)");

            List<Node> activeToRemove = removeHostsFromList(hostnames, active);
            activeToRemove = activeToRemove.stream().map(Node::unretire).collect(Collectors.toList()); // only active nodes can be retired
            nodeRepository.deactivate(activeToRemove, transaction);
            nodeRepository.activate(updateFrom(hosts, continuedActive), transaction); // update active with any changes
            nodeRepository.activate(reservedToActivate, transaction);
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

    /**
     * Returns the input nodes with the changes resulting from applying the settings in hosts to the given list of nodes.
     */
    private List<Node> updateFrom(Collection<HostSpec> hosts, List<Node> nodes) {
        List<Node> updated = new ArrayList<>();
        for (Node node : nodes) {
            HostSpec hostSpec = getHost(node.hostname(), hosts);
            node = hostSpec.membership().get().retired() ? node.retire(nodeRepository.clock().instant()) : node.unretire();
            node = node.with(node.allocation().get().with(hostSpec.membership().get()));
            if (hostSpec.flavor().isPresent()) // Docker nodes may change flavor
                node = node.with(hostSpec.flavor().get());
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
