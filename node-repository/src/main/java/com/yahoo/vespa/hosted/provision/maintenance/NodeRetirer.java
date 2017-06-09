package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Deployment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicy;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FlavorSpareChecker;

import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Automatically retires ready and active nodes if they meet a certain criteria given by the {@link RetirementPolicy}
 * and if there are enough remaining nodes to both replace the retiring node as well as to keep enough in spare.
 *
 * @author freva
 */
public class NodeRetirer extends Maintainer {
    public static final FlavorSpareChecker.SpareNodesPolicy SPARE_NODES_POLICY = flavorSpareCount ->
            flavorSpareCount.getNumReadyAmongReplacees() > 2;

    private static final long MAX_SIMULTANEOUS_RETIRES_PER_APPLICATION = 1;
    private static final Logger log = Logger.getLogger(NodeRetirer.class.getName());

    private final Deployer deployer;
    private final FlavorSpareChecker flavorSpareChecker;
    private final RetirementPolicy retirementPolicy;

    public NodeRetirer(NodeRepository nodeRepository, Zone zone, FlavorSpareChecker flavorSpareChecker, Duration interval,
                       Deployer deployer, JobControl jobControl, RetirementPolicy retirementPolicy, Zone... applies) {
        super(nodeRepository, interval, jobControl);
        if (! Arrays.asList(applies).contains(zone)) {
            String targetZones = Arrays.stream(applies).map(Zone::toString).collect(Collectors.joining(", "));
            log.info("NodeRetirer should only run in " + targetZones + " and not in " + zone + ", stopping.");
            deconstruct();
        }

        this.deployer = deployer;
        this.retirementPolicy = retirementPolicy;
        this.flavorSpareChecker = flavorSpareChecker;
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
            Map<Flavor, Map<Node.State, Long>> numSpareNodesByFlavorByState = getNumberOfNodesByFlavorByNodeState(allNodes);
            flavorSpareChecker.updateReadyAndActiveCountsByFlavor(numSpareNodesByFlavorByState);

            long numFlavorsWithUnsuccessfullyRetiredNodes = allNodes.stream()
                    .filter(node -> node.state() == Node.State.ready)
                    .filter(retirementPolicy::shouldRetire)
                    .collect(Collectors.groupingBy(
                            Node::flavor,
                            Collectors.toSet()))
                    .entrySet().stream()
                    .filter(entry -> {
                        Set<Node> nodesThatShouldBeRetiredForFlavor = entry.getValue();
                        for (Iterator<Node> iter = nodesThatShouldBeRetiredForFlavor.iterator(); iter.hasNext(); ) {
                            Node nodeToRetire = iter.next();
                            if (! flavorSpareChecker.canRetireUnallocatedNodeWithFlavor(nodeToRetire.flavor())) break;

                            nodeRepository().write(nodeToRetire.with(nodeToRetire.status().withWantToDeprovision(true)));
                            nodeRepository().park(nodeToRetire.hostname(), Agent.NodeRetirer,
                                    "Policy: " + retirementPolicy.getClass().getSimpleName());
                            iter.remove();
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

        for (ApplicationId applicationId : activeApplications) {
            Optional<Deployment> deployment = deployer.deployFromLocalActive(applicationId, Duration.ofMinutes(30));
            if ( ! deployment.isPresent()) continue; // this will be done at another config server

            long numNodesWantedToRetire = 0;
            try (Mutex lock = nodeRepository().lock(applicationId)) {
                // Get nodes for current application under lock
                List<Node> applicationNodes = nodeRepository().getNodes(applicationId);
                Set<Node> retireableNodes = getRetireableNodesForApplication(applicationNodes);
                long numNodesAllowedToRetire = getNumberNodesAllowToRetireForApplication(applicationNodes, MAX_SIMULTANEOUS_RETIRES_PER_APPLICATION);

                for (Iterator<Node> iterator = retireableNodes.iterator(); iterator.hasNext() && numNodesAllowedToRetire > numNodesWantedToRetire; ) {
                    Node retireableNode = iterator.next();

                    if (flavorSpareChecker.canRetireAllocatedNodeWithFlavor(retireableNode.flavor())) {
                        log.info("Setting wantToRetire and wantToDeprovision for host " + retireableNode.hostname() +
                                " with flavor " + retireableNode.flavor().name() +
                                " allocated to " + retireableNode.allocation().get().owner() + ". Policy: " +
                                retirementPolicy.getClass().getSimpleName());
                        Node updatedNode = retireableNode.with(retireableNode.status()
                                .withWantToRetire(true)
                                .withWantToDeprovision(true));
                        nodeRepository().write(updatedNode);
                        numNodesWantedToRetire++;
                    }
                }
            }
            if (numNodesWantedToRetire > 0) deployment.get().activate();
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

    private Map<Flavor, Map<Node.State, Long>> getNumberOfNodesByFlavorByNodeState(List<Node> allNodes) {
        return allNodes.stream()
                .collect(Collectors.groupingBy(
                        Node::flavor,
                        Collectors.groupingBy(Node::state, Collectors.counting())));
    }
}
