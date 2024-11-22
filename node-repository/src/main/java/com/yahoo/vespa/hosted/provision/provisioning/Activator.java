// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.ActivationContext;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationTransaction;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.ClusterSpec.Type;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.ParentHostUnavailableException;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.applications.ScalingEvent;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.yolean.Exceptions;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

    private static final Logger log = Logger.getLogger(Activator.class.getName());

    private final NodeRepository nodeRepository;
    private final Optional<LoadBalancerProvisioner> loadBalancerProvisioner;

    public Activator(NodeRepository nodeRepository, Optional<LoadBalancerProvisioner> loadBalancerProvisioner) {
        this.nodeRepository = nodeRepository;
        this.loadBalancerProvisioner = loadBalancerProvisioner;
    }

    /** Activate required resources for application guarded by given lock */
    public void activate(Collection<HostSpec> hosts, ActivationContext context, ApplicationTransaction transaction) {
        NodeList newActive = activateNodes(hosts, context.generation(), transaction);
        activateLoadBalancers(hosts, newActive, transaction, context.isBootstrap());
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
     * @param hosts the hosts to make the set of active nodes of this
     * @param generation the application config generation that is activated
     * @param transaction transaction with operations to commit together with any operations done within the repository,
     *                    while holding the node repository lock on this application
     * @return the nodes that will be active when transaction is committed
     */
    private NodeList activateNodes(Collection<HostSpec> hosts, long generation, ApplicationTransaction transaction) {
        Instant activationTime = nodeRepository.clock().instant(); // Use one timestamp for all activation changes
        ApplicationId application = transaction.application();
        Set<String> hostnames = hosts.stream().map(HostSpec::hostname).collect(Collectors.toSet());
        NodeList allNodes = nodeRepository.nodes().list();
        NodeList applicationNodes = allNodes.owner(application);

        NodeList reserved = applicationNodes.state(Node.State.reserved).matching(node -> hostnames.contains(node.hostname()));
        reserved = updatePortsFrom(hosts, reserved);
        nodeRepository.nodes().reserve(reserved.asList()); // Re-reserve nodes to avoid reservation expiry

        NodeList oldActive = applicationNodes.state(Node.State.active); // All nodes active now
        NodeList continuedActive = oldActive.matching(node -> hostnames.contains(node.hostname()));
        NodeList newActive = withHostInfo(continuedActive, hosts, activationTime).and(reserved); // All nodes that will be active when this is committed
        if ( ! newActive.hostnames().containsAll(hostnames))
            throw new RuntimeException("Activation of " + application + " failed, could not find all requested hosts." +
                                       "\nRequested: " + hosts +
                                       "\nReserved: " + reserved.hostnames() +
                                       "\nActive: " + oldActive.hostnames());

        validateParentHosts(application, allNodes, reserved);

        NodeList activeToRemove = oldActive.matching(node ->  ! hostnames.contains(node.hostname()));
        remove(activeToRemove, transaction); // TODO: Pass activation time in this call and next line
        nodeRepository.nodes().activate(newActive.asList(), transaction); // activate also continued active to update node state

        rememberResourceChange(transaction, generation, activationTime,
                               oldActive.not().retired(),
                               newActive.not().retired());
        unreserveParentsOf(reserved);
        return newActive;
    }

    private void remove(NodeList nodes, ApplicationTransaction transaction) {
        nodes = NodeList.copyOf(nodes.mapToList(Node::unretire)); // clear retire flag when moving to non-active state
        nodeRepository.nodes().deactivate(nodes.not().failing().asList(), transaction);
        nodeRepository.nodes().fail(nodes.failing().asList(), transaction);
    }

    private void rememberResourceChange(ApplicationTransaction transaction, long generation, Instant at,
                                        NodeList oldNodes, NodeList newNodes) {
        Optional<Application> application = nodeRepository.applications().get(transaction.application());
        if (application.isEmpty()) return; // infrastructure app, hopefully :-|

        Map<ClusterSpec.Id, NodeList> currentNodesByCluster = newNodes.groupingBy(node -> node.allocation().get().membership().cluster().id());
        Application modified = application.get();
        for (var clusterEntry : currentNodesByCluster.entrySet()) {
            var cluster = modified.cluster(clusterEntry.getKey()).get();
            var previousResources = oldNodes.cluster(clusterEntry.getKey()).toResources();
            var currentResources = clusterEntry.getValue().toResources();
            if ( previousResources.nodeResources().isUnspecified()
                 || ! previousResources.justNumbers().equals(currentResources.justNumbers())) {
                cluster = cluster.with(ScalingEvent.create(previousResources, currentResources, generation, at));
            }
            if (cluster.target().resources().isPresent()
                && cluster.target().resources().get().justNumbers().equals(currentResources.justNumbers())) {
                cluster = cluster.withTarget(cluster.target().asIdeal(nodeRepository.clock().instant()));
            }
            if (cluster != modified.cluster(clusterEntry.getKey()).get())
                modified = modified.with(cluster);
        }

        if (modified != application.get())
            nodeRepository.applications().put(modified, transaction);
    }

    /** When a tenant node is activated on a host, we can open up that host for use by others */
    private void unreserveParentsOf(NodeList nodes) {
        for (Node node : nodes) {
            if ( node.parentHostname().isEmpty()) continue;
            Optional<Node> parentNode = nodeRepository.nodes().node(node.parentHostname().get());
            if (parentNode.isEmpty()) continue;
            if (parentNode.get().reservedTo().isEmpty()) continue;

            // Above is an optimization to avoid unnecessary locking - now repeat all conditions under lock
            Optional<NodeMutex> parent = nodeRepository.nodes().lockAndGet(node.parentHostname().get());
            if (parent.isEmpty()) continue;
            try (var lock = parent.get()) {
                if (lock.node().reservedTo().isEmpty()) continue;
                nodeRepository.nodes().write(lock.node().withoutReservedTo(), lock);
            }
        }
    }

    /** Activate load balancers */
    private void activateLoadBalancers(Collection<HostSpec> hosts, NodeList newActive, ApplicationTransaction transaction, boolean isBootstrap) {
        try {
            loadBalancerProvisioner.ifPresent(provisioner -> provisioner.activate(allClustersOf(hosts), newActive, transaction));
        } catch (RuntimeException e) {
            if (isBootstrap)
                log.warning("Failed to activate load balancers for " + transaction.application() + ": " + Exceptions.toMessageString(e) + " (Ignoring because bootstrap deployment)");
            throw e;
        }
    }

    private static Set<ClusterSpec> allClustersOf(Collection<HostSpec> hosts) {
        return hosts.stream()
                    .map(HostSpec::membership)
                    .flatMap(Optional::stream)
                    .map(ClusterMembership::cluster)
                    .collect(Collectors.toUnmodifiableSet());
    }

    static void validateParentHosts(ApplicationId application, NodeList allNodes, NodeList potentialChildren) {
        Set<String> parentHostnames = potentialChildren.stream()
                .map(Node::parentHostname)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());

        Set<String> nonActiveHosts = allNodes.not().state(Node.State.active)
                                             .matching(node -> parentHostnames.contains(node.hostname()))
                                             .hostnames();

        Set<String> applicationParentHostnames = new HashSet<>(parentHostnames);
        applicationParentHostnames.removeIf(host -> potentialChildren.childrenOf(host).type(Type.combined, Type.container, Type.content).isEmpty());

        Set<String> nonActiveApplicationHosts = new HashSet<>(nonActiveHosts);
        nonActiveApplicationHosts.removeIf(host -> potentialChildren.childrenOf(host).type(Type.combined, Type.container, Type.content).isEmpty());

        if (!nonActiveHosts.isEmpty()) {
            int numActiveApplication = applicationParentHostnames.size() - nonActiveApplicationHosts.size();
            int numActiveAdmin = parentHostnames.size() - nonActiveHosts.size() - numActiveApplication;
            var messageBuilder = new StringBuilder()
                    .append(numActiveApplication).append("/").append(applicationParentHostnames.size())
                    .append(" application hosts");
            if (parentHostnames.size() > applicationParentHostnames.size())
                messageBuilder.append(" and ")
                              .append(numActiveAdmin).append("/").append(parentHostnames.size() - applicationParentHostnames.size())
                              .append(" admin hosts");
            messageBuilder.append(" for ").append(application)
                          .append(" have completed provisioning and bootstrapping, still waiting for ");

            if (nonActiveHosts.size() <= 5) {
                messageBuilder.append(nonActiveHosts.stream()
                        .sorted()
                        .collect(Collectors.joining(", ")));
            } else {
                messageBuilder.append(nonActiveHosts.stream()
                        .sorted()
                        .limit(3)
                        .collect(Collectors.joining(", ")))
                        .append(", and others");
            }
            var message = messageBuilder.toString();

            throw new ParentHostUnavailableException(message);
        }
    }

    /** Returns the input nodes with the changes resulting from applying the settings in hosts to the given list of nodes. */
    private NodeList withHostInfo(NodeList nodes, Collection<HostSpec> hosts, Instant at) {
        List<Node> updated = new ArrayList<>();
        for (Node node : nodes) {
            HostSpec hostSpec = getHost(node.hostname(), hosts);
            node = hostSpec.membership().get().retired() ? node.retire(at) : node.unretire();
            if (! hostSpec.advertisedResources().equals(node.resources())) // A resized node
                node = node.with(new Flavor(hostSpec.advertisedResources()), Agent.application, at);
            Allocation allocation = node.allocation().get()
                                        .with(hostSpec.membership().get())
                                        .withRequestedResources(hostSpec.requestedResources()
                                                                        .orElse(node.resources()));
            if (hostSpec.networkPorts().isPresent())
                allocation = allocation.withNetworkPorts(hostSpec.networkPorts().get());
            node = node.with(allocation);
            updated.add(node);
        }
        return NodeList.copyOf(updated);
    }

    /**
     * Returns the input nodes with any port allocations from the hosts
     */
    private NodeList updatePortsFrom(Collection<HostSpec> hosts, NodeList nodes) {
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
        return NodeList.copyOf(updated);
    }

    private HostSpec getHost(String hostname, Collection<HostSpec> fromHosts) {
        for (HostSpec host : fromHosts)
            if (host.hostname().equals(hostname))
                return host;
        return null;
    }

}
