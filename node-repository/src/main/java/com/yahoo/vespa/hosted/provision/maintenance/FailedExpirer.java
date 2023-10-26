// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.Node.State;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History.Event.Type;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Logger;

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
        Predicate<Node> isExpired = node ->    node.state() == State.failed
                                            && node.history().hasEventBefore(Type.failed, clock().instant().minus(expiryFor(node)));
        NodeList allNodes = nodeRepository.nodes().list(); // Stale snapshot, not critical.

        nodeRepository.nodes().performOn(allNodes.nodeType(NodeType.tenant),
                                         isExpired,
                                         (node, lock) -> recycle(node, List.of(), allNodes).get());

        nodeRepository.nodes().performOnRecursively(allNodes.nodeType(NodeType.host).matching(isExpired),
                                                    nodes -> isExpired.test(nodes.parent().node()),
                                                    nodes -> recycle(nodes.parent().node(),
                                                                     nodes.children().stream().map(NodeMutex::node).toList(),
                                                                     allNodes)
                                                            .map(List::of).orElse(List.of()));
        return 1.0;
    }

    private Duration expiryFor(Node node) {
        return node.allocation().isEmpty() ? Duration.ZERO
                                           : node.allocation().get().membership().cluster().isStateful() ? statefulExpiry
                                                                                                         : statelessExpiry;
    }

    private Optional<Node> recycle(Node node, List<Node> children, NodeList allNodes) {
        Optional<String> reason = shouldPark(node, allNodes);
        if (reason.isPresent()) {
            List<String> unparkedChildren = children.stream()
                                                    .filter(child -> child.state() != Node.State.parked)
                                                    .map(Node::hostname)
                                                    .toList();
            if (unparkedChildren.isEmpty()) {
                // Only forcing de-provisioning of off premises nodes
                return Optional.of(nodeRepository.nodes().park(node.hostname(), nodeRepository.zone().cloud().dynamicProvisioning(), Agent.FailedExpirer,
                                                               "Parked by FailedExpirer due to " + reason.get()));
            } else {
                log.info(String.format("Expired failed node %s was not parked because of unparked children: %s",
                                       node.hostname(), String.join(", ", unparkedChildren)));
                return Optional.empty();
            }
        } else {
            return Optional.of(nodeRepository.nodes().deallocate(node, Agent.FailedExpirer, "Expired by FailedExpirer"));
        }
    }

    /** Returns whether the node should be parked instead of recycled */
    private Optional<String> shouldPark(Node node, NodeList allNodes) {
        if (NodeFailer.hasHardwareIssue(node, allNodes))
            return Optional.of("has hardware issues");
        if (node.type().isHost() && node.status().failCount() >= maxAllowedFailures)
            return Optional.of("has failed too many times");
        if (node.status().wantToDeprovision())
            return Optional.of("want to deprovision");
        if (node.status().wantToRetire())
            return Optional.of("want to retire");
        return Optional.empty();
    }

}
