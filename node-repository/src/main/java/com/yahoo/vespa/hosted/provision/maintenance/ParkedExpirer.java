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
import com.yahoo.vespa.hosted.provision.node.Report;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.provision.maintenance.FailedExpirer.maxAllowedFailures;

/**
 * This moves expired parked nodes:
 *
 * - To dirty: if it does not have a hardware failure.
 * - Otherwise the node will remain in parked.
 *
 * Nodes with detected hardware issues will not be recycled.
 *
 * @author bjormel
 */
public class ParkedExpirer extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(ParkedExpirer.class.getName());

    private final NodeRepository nodeRepository;
    private final Duration expiry; // Grace period to allow operators to inspect

    ParkedExpirer(NodeRepository nodeRepository, Zone zone, Duration interval, Metric metric) {
        super(nodeRepository, interval, metric);
        this.nodeRepository = nodeRepository;
        if (zone.system().isCd()) {
            expiry = Duration.ofMinutes(30);
        } else {
            if (zone.environment() == Environment.staging || zone.environment() == Environment.test) {
                expiry = Duration.ofMinutes(10);
            } else {
                expiry = Duration.ofDays(4);
            }
        }
    }

    @Override
    protected double maintain() {
        NodeList allNodes = nodeRepository.nodes().list();
        List<Node> remainingNodes = new ArrayList<>(allNodes.state(Node.State.parked)
                .nodeType(NodeType.tenant, NodeType.host)
                .asList());

        recycleIf(node -> node.allocation().isEmpty(), remainingNodes);
        recycleIf(node -> node.allocation().isPresent() &&
                        node.history().hasEventBefore(History.Event.Type.parked, clock().instant().minus(expiry)),
                remainingNodes);
        return 1.0;
    }

    /** Recycle the nodes matching condition, and remove those nodes from the nodes list. */
    private void recycleIf(Predicate<Node> condition, List<Node> parkedNodes) {
        List<Node> nodesToRecycle = parkedNodes.stream().filter(condition).toList();
        parkedNodes.removeAll(nodesToRecycle);
        recycle(nodesToRecycle);
    }

    /** Move eligible nodes to dirty. This may be a subset of the given nodes */
    private void recycle(List<Node> nodes) {
        List<Node> nodesToRecycle = new ArrayList<>();
        for (Node candidate : nodes) {
            if (candidate.type().isHost()
                && (candidate.status().failCount() >= maxAllowedFailures
                   || candidate.reports().getReports().stream().anyMatch(report -> report.getType().equals(Report.Type.HARD_FAIL)))) {
                log.info(String.format("Not recycling due to hardware failure or max allowed failures: %s", candidate.hostname()));
            } else {
                nodesToRecycle.add(candidate);
            }
        }
        nodeRepository.nodes().deallocate(nodesToRecycle, Agent.ParkedExpirer, "Expired by ParkedExpirer");
    }

}
