// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.log.LogLevel;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicy;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorSpareChecker;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Automatically retires ready and active nodes if they meet a certain criteria given by the {@link RetirementPolicy}
 * and if there are enough remaining nodes to both replace the retiring node as well as to keep enough in spare.
 *
 * @author freva
 */
public class NodeRetirer extends Maintainer {

    public static final FlavorSpareChecker.SpareNodesPolicy SPARE_NODES_POLICY = flavorSpareCount ->
            flavorSpareCount.getNumReadyAmongReplacees() > 2;

    private static final long MAX_SIMULTANEOUS_RETIRES_PER_CLUSTER = 1;
    private static final Logger log = Logger.getLogger(NodeRetirer.class.getName());

    private final Deployer deployer;
    private final FlavorSpareChecker flavorSpareChecker;
    private final RetirementPolicy retirementPolicy;

    public NodeRetirer(NodeRepository nodeRepository, FlavorSpareChecker flavorSpareChecker, Duration interval,
                       Deployer deployer, JobControl jobControl, RetirementPolicy retirementPolicy) {
        super(nodeRepository, interval, jobControl);
        this.deployer = deployer;
        this.retirementPolicy = retirementPolicy;
        this.flavorSpareChecker = flavorSpareChecker;
    }

    @Override
    protected void maintain() {
        if (! retirementPolicy.isActive()) return;

        if (retireUnallocated()) {
            retireAllocated();
        }
    }

    /**
     * Retires unallocated nodes by moving them directly to parked.
     * Returns true iff all there are no unallocated nodes that match the retirement policy
     */
    boolean retireUnallocated() {
        try (Mutex lock = nodeRepository().lockUnallocated()) {
            List<Node> allNodes = nodeRepository().getNodes(NodeType.tenant);
            Map<Flavor, Map<Node.State, Long>> numSpareNodesByFlavorByState = getNumberOfNodesByFlavorByNodeState(allNodes);
            flavorSpareChecker.updateReadyAndActiveCountsByFlavor(numSpareNodesByFlavorByState);

            long numFlavorsWithUnsuccessfullyRetiredNodes = allNodes.stream()
                    .filter(node -> node.state() == Node.State.ready)
                    .filter(node -> retirementPolicy.shouldRetire(node).isPresent())
                    .collect(Collectors.groupingBy(
                            Node::flavor,
                            Collectors.toSet()))
                    .entrySet().stream()
                    .filter(entry -> {
                        Set<Node> nodesThatShouldBeRetiredForFlavor = entry.getValue();
                        for (Iterator<Node> iter = nodesThatShouldBeRetiredForFlavor.iterator(); iter.hasNext(); ) {
                            Node nodeToRetire = iter.next();
                            if (! flavorSpareChecker.canRetireUnallocatedNodeWithFlavor(nodeToRetire.flavor())) break;

                            retirementPolicy.shouldRetire(nodeToRetire).ifPresent(reason -> {
                                nodeRepository().write(nodeToRetire.with(nodeToRetire.status().withWantToDeprovision(true)));
                                nodeRepository().park(nodeToRetire.hostname(), Agent.NodeRetirer, reason);
                                iter.remove();
                            });
                        }

                        if (! nodesThatShouldBeRetiredForFlavor.isEmpty()) {
                            String commaSeparatedHostnames = nodesThatShouldBeRetiredForFlavor.stream().map(Node::hostname)
                                    .collect(Collectors.joining(", "));
                            log.info(String.format("Failed to retire %s, wanted to retire %d nodes (%s), but there are no spare nodes left.",
                                    entry.getKey(), nodesThatShouldBeRetiredForFlavor.size(), commaSeparatedHostnames));
                        }
                        return ! nodesThatShouldBeRetiredForFlavor.isEmpty();
                    }).count();

            return numFlavorsWithUnsuccessfullyRetiredNodes == 0;
        }
    }

    void retireAllocated() {
        List<Node> allNodes = nodeRepository().getNodes(NodeType.tenant);
        List<ApplicationId> activeApplications = getActiveApplicationIds(allNodes);
        Map<Flavor, Map<Node.State, Long>> numSpareNodesByFlavorByState = getNumberOfNodesByFlavorByNodeState(allNodes);
        flavorSpareChecker.updateReadyAndActiveCountsByFlavor(numSpareNodesByFlavorByState);

        // Get all the nodes that we could retire along with their deployments
        Map<Deployment, Set<Node>> nodesToRetireByDeployment = new HashMap<>();
        for (ApplicationId applicationId : activeApplications) {
            Map<ClusterSpec.Id, Set<Node>> nodesByCluster = getNodesBelongingToApplication(allNodes, applicationId).stream()
                    .collect(Collectors.groupingBy(
                            node -> node.allocation().get().membership().cluster().id(),
                            Collectors.toSet()));
            Map<ClusterSpec.Id, Set<Node>> retireableNodesByCluster = nodesByCluster.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> filterRetireableNodes(entry.getValue())));
            if (retireableNodesByCluster.values().stream().mapToInt(Set::size).sum() == 0) continue;

            Optional<Deployment> deployment = deployer.deployFromLocalActive(applicationId);
            if ( ! deployment.isPresent()) continue; // this will be done at another config server

            Set<Node> replaceableNodes = retireableNodesByCluster.entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                            .filter(node -> flavorSpareChecker.canRetireAllocatedNodeWithFlavor(node.flavor()))
                            .limit(getNumberNodesAllowToRetireForCluster(nodesByCluster.get(entry.getKey()), MAX_SIMULTANEOUS_RETIRES_PER_CLUSTER)))
                    .collect(Collectors.toSet());
            if (! replaceableNodes.isEmpty()) nodesToRetireByDeployment.put(deployment.get(), replaceableNodes);
        }

        nodesToRetireByDeployment.forEach(((deployment, nodes) -> {
            ApplicationId app = nodes.iterator().next().allocation().get().owner();
            Set<Node> nodesToRetire;

            // While under application lock, get up-to-date node, and make sure that the state and the owner of the
            // node has not changed in the meantime, mutate the up-to-date node (so to not overwrite other fields
            // that may have changed) with wantToRetire and wantToDeprovision.
            try (Mutex lock = nodeRepository().lock(app)) {
                nodesToRetire = nodes.stream()
                        .map(node ->
                                nodeRepository().getNode(node.hostname())
                                        .filter(upToDateNode -> node.state() == Node.State.active)
                                        .filter(upToDateNode -> node.allocation().get().owner().equals(upToDateNode.allocation().get().owner())))
                        .flatMap(node -> node.map(Stream::of).orElseGet(Stream::empty))
                        .collect(Collectors.toSet());

                nodesToRetire.forEach(node ->
                        retirementPolicy.shouldRetire(node).ifPresent(reason -> {
                            log.info("Setting wantToRetire and wantToDeprovision for host " + node.hostname() +
                                    " with flavor " + node.flavor().name() +
                                    " allocated to " + node.allocation().get().owner() + ". Reason: " + reason);

                            Node updatedNode = node.with(node.status()
                                    .withWantToRetire(true)
                                    .withWantToDeprovision(true));
                            nodeRepository().write(updatedNode);
                        }));
            }

            // This takes a while, so do it outside of the application lock
            if (! nodesToRetire.isEmpty()) {
                try {
                    deployment.activate();
                } catch (Exception e) {
                    log.log(LogLevel.INFO, "Failed to redeploy " + app.serializedForm() + ", will be redeployed later by application maintainer", e);
                }
            }
        }));
    }

    private List<Node> getNodesBelongingToApplication(Collection<Node> allNodes, ApplicationId applicationId) {
        return allNodes.stream()
                .filter(node -> node.allocation().isPresent())
                .filter(node -> node.allocation().get().owner().equals(applicationId))
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of ApplicationIds sorted by number of active nodes the application has allocated to it
     */
    List<ApplicationId> getActiveApplicationIds(Collection<Node> nodes) {
        return nodes.stream()
                .filter(node -> node.state() == Node.State.active)
                .collect(Collectors.groupingBy(
                        node -> node.allocation().get().owner(),
                        Collectors.counting()))
                .entrySet().stream()
                .sorted((c1, c2) -> c2.getValue().compareTo(c1.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * @param nodes Collection of nodes that are considered for retirement
     * @return Set of nodes that all should eventually be retired
     */
    Set<Node> filterRetireableNodes(Collection<Node> nodes) {
        return nodes.stream()
                .filter(node -> node.state() == Node.State.active)
                .filter(node -> !node.status().wantToRetire())
                .filter(node -> retirementPolicy.shouldRetire(node).isPresent())
                .collect(Collectors.toSet());
    }

    /**
     * @param clusterNodes All the nodes allocated to an application belonging to a single cluster
     * @return number of nodes we can safely start retiring
     */
    long getNumberNodesAllowToRetireForCluster(Collection<Node> clusterNodes, long maxSimultaneousRetires) {
        long numNodesInWantToRetire = clusterNodes.stream()
                .filter(node -> node.status().wantToRetire())
                .filter(node -> node.state() != Node.State.parked)
                .count();
        return Math.max(0, maxSimultaneousRetires - numNodesInWantToRetire);
    }

    private Map<Flavor, Map<Node.State, Long>> getNumberOfNodesByFlavorByNodeState(Collection<Node> allNodes) {
        return allNodes.stream()
                .collect(Collectors.groupingBy(
                        Node::flavor,
                        Collectors.groupingBy(Node::state, Collectors.counting())));
    }

}
