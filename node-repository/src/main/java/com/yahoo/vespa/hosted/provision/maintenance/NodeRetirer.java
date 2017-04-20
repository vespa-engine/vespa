package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Flavor;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.maintenance.retire.RetirementPolicy;
import com.yahoo.vespa.hosted.provision.node.Agent;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author freva
 */
public class NodeRetirer extends Maintainer {
    private final RetirementPolicy retirementPolicy;

    public NodeRetirer(NodeRepository nodeRepository, Duration interval, RetirementPolicy retirementPolicy) {
        super(nodeRepository, interval);
        this.retirementPolicy = retirementPolicy;
    }

    @Override
    protected void maintain() {

    }

    boolean retireUnallocated() {
        try (Mutex lock = nodeRepository().lockUnallocated()) {
            List<Node> allNodes = nodeRepository().getNodes();
            Map<Flavor, Long> numSpareNodesByFlavor = getNumberSpareReadyNodesByFlavor(allNodes);

            long numFlavorsWithUnsuccessfullyRetiredNodes = allNodes.stream()
                    .filter(node -> node.state() == Node.State.ready)
                    .filter(retirementPolicy::shouldRetire)
                    .collect(Collectors.groupingBy(
                            Node::flavor,
                            Collectors.toSet()))
                    .entrySet().stream()
                    .filter(entry -> {
                        long numSpareReadyNodesForCurrentFlavor = numSpareNodesByFlavor.get(entry.getKey());
                        entry.getValue().stream()
                                .limit(numSpareReadyNodesForCurrentFlavor)
                                .forEach(node -> nodeRepository().park(node.hostname(), Agent.system));

                        return numSpareReadyNodesForCurrentFlavor < entry.getValue().size();
                    }).count();

            return numFlavorsWithUnsuccessfullyRetiredNodes == 0;
        }
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
                            long numNodesToToSpare = (long) Math.max(2, 0.1 * numActiveNodesByCurrentFlavor);
                            return Math.max(0L, entry.getValue() - numNodesToToSpare);
                }));
    }

    @Override
    public String toString() {
        return "Node retirer";
    }
}
