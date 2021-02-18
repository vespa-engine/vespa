// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
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
    // We will stop giving the nodes back to Openstack for break-fix, setting this to number a high value, we might
    // eventually remove this counter and recycling nodes forever
    private static final int maxAllowedFailures = 50;

    private final NodeRepository nodeRepository;
    private final Duration defaultExpiry; // Grace period to allow recovery of data
    private final Duration containerExpiry; // Stateless nodes, no data to recover

    FailedExpirer(NodeRepository nodeRepository, Zone zone, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.nodeRepository = nodeRepository;
        if (zone.system().isCd()) {
            defaultExpiry = containerExpiry = Duration.ofMinutes(30);
        } else {
            if (zone.environment() == Environment.staging || zone.environment() == Environment.test) {
                defaultExpiry = Duration.ofHours(1);
            } else {
                defaultExpiry = Duration.ofDays(4);
            }
            containerExpiry = Duration.ofHours(1);
        }
    }

    @Override
    protected boolean maintain() {
        List<Node> remainingNodes = nodeRepository.nodes().list(Node.State.failed).stream()
                                                  .filter(node -> node.type() == NodeType.tenant ||
                                                                  node.type() == NodeType.host)
                                                  .collect(Collectors.toList());

        recycleIf(remainingNodes, node -> node.allocation().isEmpty());
        recycleIf(remainingNodes, node ->
                node.allocation().get().membership().cluster().type() == ClusterSpec.Type.container &&
                node.history().hasEventBefore(History.Event.Type.failed, clock().instant().minus(containerExpiry)));
        recycleIf(remainingNodes, node ->
                node.history().hasEventBefore(History.Event.Type.failed, clock().instant().minus(defaultExpiry)));
        return true;
    }

    /** Recycle the nodes matching condition, and remove those nodes from the nodes list. */
    private void recycleIf(List<Node> nodes, Predicate<Node> recycleCondition) {
        List<Node> nodesToRecycle = nodes.stream().filter(recycleCondition).collect(Collectors.toList());
        nodes.removeAll(nodesToRecycle);
        recycle(nodesToRecycle);
    }

    /** Move eligible nodes to dirty. This may be a subset of the given nodes */
    private void recycle(List<Node> nodes) {
        List<Node> nodesToRecycle = new ArrayList<>();
        for (Node candidate : nodes) {
            if (NodeFailer.hasHardwareIssue(candidate, nodeRepository)) {
                List<String> unparkedChildren = !candidate.type().isHost() ? List.of() :
                                                nodeRepository.nodes().list()
                                                              .childrenOf(candidate)
                                                              .matching(node -> node.state() != Node.State.parked)
                                                              .mapToList(Node::hostname);

                if (unparkedChildren.isEmpty()) {
                    nodeRepository.nodes().park(candidate.hostname(), false, Agent.FailedExpirer,
                                                "Parked by FailedExpirer due to hardware issue");
                } else {
                    log.info(String.format("Expired failed node %s with hardware issue was not parked because of " +
                                           "unparked children: %s", candidate.hostname(),
                                           String.join(", ", unparkedChildren)));
                }
            } else if (!failCountIndicatesHardwareIssue(candidate)) {
                nodesToRecycle.add(candidate);
            }
        }
        nodeRepository.nodes().deallocate(nodesToRecycle, Agent.FailedExpirer, "Expired by FailedExpirer");
    }

    /** Returns whether the current node fail count should be used as an indicator of hardware issue */
    private boolean failCountIndicatesHardwareIssue(Node node) {
        return node.type().isHost() && node.status().failCount() >= maxAllowedFailures;
    }

}
