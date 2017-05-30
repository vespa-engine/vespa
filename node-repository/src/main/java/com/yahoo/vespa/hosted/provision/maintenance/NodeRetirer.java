package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicy;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorClusters;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author freva
 */
public class NodeRetirer extends Maintainer {
    private static final long MAX_SIMULTANEOUS_RETIRES_PER_APPLICATION = 1;
    private static final Logger log = Logger.getLogger(NodeRetirer.class.getName());

    private final FlavorClusters flavorClusters;
    private final RetirementPolicy retirementPolicy;

    public NodeRetirer(NodeRepository nodeRepository, Zone zone, FlavorClusters flavorClusters, Duration interval,
                       JobControl jobControl, RetirementPolicy retirementPolicy, Zone... applies) {
        super(nodeRepository, interval, jobControl);
        if (! Arrays.asList(applies).contains(zone)) {
            String targetZones = Arrays.stream(applies).map(Zone::toString).collect(Collectors.joining(", "));
            log.info("NodeRetirer should only run in " + targetZones + " and not in " + zone + ", stopping.");
            deconstruct();
        }

        this.retirementPolicy = retirementPolicy;
        this.flavorClusters = flavorClusters;
    }

    @Override
    protected void maintain() {
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
            Map<Flavor, Long> numSpareNodesByFlavor = getNumberSpareReadyNodesByFlavor(allNodes);

            long numFlavorsWithUnsuccessfullyRetiredNodes = allNodes.stream()
                    .filter(node -> node.state() == Node.State.ready)
                    .filter(retirementPolicy::shouldRetire)
                    .collect(Collectors.groupingBy(
                            Node::flavor,
                            Collectors.toSet()))
                    .entrySet().stream()
                    .filter(entry -> {
                        Set<Node> nodesThatShouldBeRetiredForFlavor = entry.getValue();
                        long numSpareReadyNodesForFlavor = numSpareNodesByFlavor.get(entry.getKey());
                        boolean parkedAll = limitedPark(nodesThatShouldBeRetiredForFlavor, numSpareReadyNodesForFlavor);
                        if (!parkedAll) {
                            String commaSeparatedHostnames = nodesThatShouldBeRetiredForFlavor.stream().map(Node::hostname)
                                    .collect(Collectors.joining(", "));
                            log.info(String.format("Failed to retire %s, wanted to retire %d nodes (%s), but only %d spare " +
                                            "nodes available for flavor cluster.",
                                    entry.getKey(), nodesThatShouldBeRetiredForFlavor.size(), commaSeparatedHostnames, numSpareReadyNodesForFlavor));
                        }
                        return !parkedAll;
                    }).count();

            return numFlavorsWithUnsuccessfullyRetiredNodes == 0;
        }
    }

    void retireAllocated() {
        List<Node> allNodes = nodeRepository().getNodes(NodeType.tenant);
        List<ApplicationId> activeApplications = getActiveApplicationIds(allNodes);
        Map<Flavor, Long> numSpareNodesByFlavor = getNumberSpareReadyNodesByFlavor(allNodes);

        for (ApplicationId applicationId : activeApplications) {
            try (Mutex lock = nodeRepository().lock(applicationId)) {
                // Get nodes for current application under lock
                List<Node> applicationNodes = nodeRepository().getNodes(applicationId);
                Set<Node> retireableNodes = getRetireableNodesForApplication(applicationNodes);
                long numNodesAllowedToRetire = getNumberNodesAllowToRetireForApplication(applicationNodes, MAX_SIMULTANEOUS_RETIRES_PER_APPLICATION);

                for (Iterator<Node> iterator = retireableNodes.iterator(); iterator.hasNext() && numNodesAllowedToRetire > 0; ) {
                    Node retireableNode = iterator.next();

                    Set<Flavor> possibleReplacementFlavors = flavorClusters.getFlavorClusterFor(retireableNode.flavor());
                    Flavor flavorWithMinSpareNodes = getMinAmongstKeys(numSpareNodesByFlavor, possibleReplacementFlavors);
                    long spareNodesForMinFlavor = numSpareNodesByFlavor.getOrDefault(flavorWithMinSpareNodes, 0L);
                    if (spareNodesForMinFlavor > 0) {
                        log.info("Setting node " + retireableNode + " to wantToRetire. Policy: " +
                                retirementPolicy.getClass().getSimpleName());
                        Node updatedNode = retireableNode.with(retireableNode.status().withWantToRetire(true));
                        nodeRepository().write(updatedNode);
                        numSpareNodesByFlavor.put(flavorWithMinSpareNodes, spareNodesForMinFlavor - 1);
                        numNodesAllowedToRetire--;
                    }
                }
            }
        }
    }

    /**
     * Returns a list of ApplicationIds sorted by number of active nodes the application has allocated to it
     */
    List<ApplicationId> getActiveApplicationIds(List<Node> nodes) {
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
     * @param applicationNodes All the nodes allocated to an application
     * @return Set of nodes that all should eventually be retired
     */
    Set<Node> getRetireableNodesForApplication(List<Node> applicationNodes) {
        return applicationNodes.stream()
                .filter(node -> node.state() == Node.State.active)
                .filter(node -> !node.status().wantToRetire())
                .filter(retirementPolicy::shouldRetire)
                .collect(Collectors.toSet());
    }

    /**
     * @param applicationNodes All the nodes allocated to an application
     * @return number of nodes we can safely start retiring
     */
    long getNumberNodesAllowToRetireForApplication(List<Node> applicationNodes, long maxSimultaneousRetires) {
        long numNodesInWantToRetire = applicationNodes.stream()
                .filter(node -> node.status().wantToRetire())
                .filter(node -> node.state() != Node.State.parked)
                .count();
        return Math.max(0, maxSimultaneousRetires - numNodesInWantToRetire);
    }

    /**
     * @param nodesToPark Nodes that we want to park
     * @param limit Maximum number of nodes we want to park
     * @return True iff we were able to park all the nodes
     */
    boolean limitedPark(Set<Node> nodesToPark, long limit) {
        nodesToPark.stream()
                .limit(limit)
                .forEach(node -> nodeRepository().park(node.hostname(), Agent.NodeRetirer, "Policy: " + retirementPolicy.getClass().getSimpleName()));

        return limit >= nodesToPark.size();
    }

    Map<Flavor, Long> getNumberSpareReadyNodesByFlavor(List<Node> allNodes) {
        Map<Flavor, Long> numActiveNodesByFlavor = allNodes.stream()
                .filter(node -> node.state() == Node.State.active)
                .collect(Collectors.groupingBy(Node::flavor, Collectors.counting()));

        return allNodes.stream()
                .filter(node -> node.state() == Node.State.ready)
                .collect(Collectors.groupingBy(Node::flavor, Collectors.counting()))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            long numActiveNodesByCurrentFlavor = numActiveNodesByFlavor.getOrDefault(entry.getKey(), 0L);
                            return getNumSpareNodes(numActiveNodesByCurrentFlavor, entry.getValue());
                }));
    }

    /**
     * Returns number of ready nodes to spare (beyond a safety buffer) for a flavor given its number of active
     * and ready nodes.
     */
    long getNumSpareNodes(long numActiveNodes, long numReadyNodes) {
        long numNodesToSpare = 2;
        return Math.max(0L, numReadyNodes - numNodesToSpare);
    }

    /**
     * Returns the key with the smallest value amongst keys
     */
    <K, V extends Comparable<V>> K getMinAmongstKeys(Map<K, V> map, Set<K> keys) {
        return map.entrySet().stream()
                .filter(entry -> keys.contains(entry.getKey()))
                .min(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new RuntimeException("No min key found"));
    }
}
