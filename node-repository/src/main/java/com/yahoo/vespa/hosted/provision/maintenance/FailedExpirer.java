// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This moves expired failed nodes:
 *
 * - To parked: If the node has known hardware failure, hosts are moved to parked only when all of their
 *              children are already in parked.
 * - To dirty: If the node is a host and has failed less than 5 times, or always if the node is a child.
 * - Otherwise the node will remain in failed.
 *
 * Failed content nodes are given a long expiry time to enable us to manually moved them back to
 * active to recover data in cases where the node was failed accidentally.
 *
 * Failed containers (Vespa, not Linux) are expired early as there's no data to potentially recover.
 *
 * The purpose of the automatic recycling to dirty + fail count is that nodes which were moved
 * to failed due to some undetected hardware failure will end up being failed again.
 * When that has happened enough they will not be recycled, and need manual inspection to move on.
 *
 * Nodes with detected hardware issues will not be recycled.
 *
 * @author bratseth
 * @author mpolden
 */
public class FailedExpirer extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(FailedExpirer.class.getName());
    // Try recycling nodes until reaching this many failures
    private static final int maxAllowedFailures = 50;

    private final NodeRepository nodeRepository;
    private final Duration statefulExpiry; // Stateful nodes: Grace period to allow recovery of data
    private final Duration statelessExpiry; // Stateless nodes: No data to recover

    FailedExpirer(NodeRepository nodeRepository, Zone zone, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.nodeRepository = nodeRepository;
        if (zone.system().isCd()) {
            statefulExpiry = statelessExpiry = Duration.ofMinutes(30);
        } else {
            if (zone.environment() == Environment.staging || zone.environment() == Environment.test) {
                statefulExpiry = Duration.ofHours(1);
            } else {
                statefulExpiry = Duration.ofDays(4);
            }
            statelessExpiry = Duration.ofHours(1);
        }
    }

    @Override
    protected double maintain() {
        NodeList allNodes = nodeRepository.nodes().list();
        List<Node> remainingNodes = new ArrayList<>(allNodes.state(Node.State.failed)
                                                            .nodeType(NodeType.tenant, NodeType.host)
                                                            .asList());

        recycleIf(node -> node.allocation().isEmpty(), remainingNodes, allNodes);
        recycleIf(node -> !node.allocation().get().membership().cluster().isStateful() &&
                node.history().hasEventBefore(History.Event.Type.failed, clock().instant().minus(statelessExpiry)),
                  remainingNodes,
                  allNodes);
        recycleIf(node -> node.allocation().get().membership().cluster().isStateful() &&
                          node.history().hasEventBefore(History.Event.Type.failed, clock().instant().minus(statefulExpiry)),
                  remainingNodes,
                  allNodes);
        return 1.0;
    }

    /** Recycle the nodes matching condition, and remove those nodes from the nodes list. */
    private void recycleIf(Predicate<Node> condition, List<Node> failedNodes, NodeList allNodes) {
        List<Node> nodesToRecycle = failedNodes.stream().filter(condition).collect(Collectors.toList());
        failedNodes.removeAll(nodesToRecycle);
        recycle(nodesToRecycle, allNodes);
    }

    /** Move eligible nodes to dirty or parked. This may be a subset of the given nodes */
    private void recycle(List<Node> nodes, NodeList allNodes) {
        List<Node> nodesToRecycle = new ArrayList<>();
        for (Node candidate : nodes) {
            if (broken(candidate, allNodes)) {
                List<String> unparkedChildren = !candidate.type().isHost() ? List.of() :
                                                allNodes.childrenOf(candidate)
                                                        .not().state(Node.State.parked)
                                                        .mapToList(Node::hostname);

                if (unparkedChildren.isEmpty()) {
                    nodeRepository.nodes().park(candidate.hostname(), true, Agent.FailedExpirer,
                                                "Parked by FailedExpirer due to hardware issue or high fail count");
                } else {
                    log.info(String.format("Expired failed node %s with hardware issue was not parked because of " +
                                           "unparked children: %s",
                                           candidate.hostname(), String.join(", ", unparkedChildren)));
                }
            } else {
                nodesToRecycle.add(candidate);
            }
        }
        nodeRepository.nodes().deallocate(nodesToRecycle, Agent.FailedExpirer, "Expired by FailedExpirer");
    }

    /** Returns whether node is broken and cannot be recycled */
    private boolean broken(Node node, NodeList allNodes) {
        return NodeFailer.hasHardwareIssue(node, allNodes) ||
               (node.type().isHost() && node.status().failCount() >= maxAllowedFailures);
    }

}
