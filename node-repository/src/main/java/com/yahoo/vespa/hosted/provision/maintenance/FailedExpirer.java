// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.History;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This moves expired failed nodes:
 * <ul>
 *     <li>To parked: If the node has known hardware failure, Docker hosts are moved to parked only when all of their
 *     children are already in parked
 *     <li>To dirty: If the node has failed less than 5 times OR the environment is dev, test or perf.
 *     Those environments have no protection against users running bogus applications, so
 *     we cannot use the node failure count to conclude the node has a failure.
 *     <li>Otherwise the node will remain in failed
 * </ul>
 * Failed content nodes are given a long expiry time to enable us to manually moved them back to
 * active to recover data in cases where the node was failed accidentally.
 * <p>
 * Failed container (Vespa, not Docker) nodes are expired early as there's no data to potentially recover.
 * </p>
 * <p>
 * The purpose of the automatic recycling to dirty + fail count is that nodes which were moved
 * to failed due to some undetected hardware failure will end up being failed again.
 * When that has happened enough they will not be recycled.
 * <p>
 * The Chef recipe running locally on the node may set hardwareFailureDescription to avoid the node
 * being automatically recycled in cases where an error has been positively detected.
 *
 * @author bratseth
 * @author mpolden
 */
public class FailedExpirer extends Maintainer {

    private static final Logger log = Logger.getLogger(NodeRetirer.class.getName());
    private static final int maxAllowedFailures = 5; // Stop recycling nodes after this number of failures

    private final NodeRepository nodeRepository;
    private final Zone zone;
    private final Clock clock;
    private final Duration defaultExpiry; // Grace period to allow recovery of data
    private final Duration containerExpiry; // Stateless nodes, no data to recover

    public FailedExpirer(NodeRepository nodeRepository, Zone zone, Clock clock, Duration interval,
                         JobControl jobControl) {
        super(nodeRepository, interval, jobControl);
        this.nodeRepository = nodeRepository;
        this.zone = zone;
        this.clock = clock;
        if (zone.system() == SystemName.main) {
            defaultExpiry = Duration.ofDays(4);
            containerExpiry = Duration.ofHours(1);
        } else {
            defaultExpiry = containerExpiry = Duration.ofMinutes(30);
        }
    }

    @Override
    protected void maintain() {
        List<Node> containerNodes = getExpiredNodes(containerExpiry)
                .stream()
                .filter(node -> node.allocation().isPresent() &&
                                node.allocation().get().membership().cluster().type() == ClusterSpec.Type.container)
                .collect(Collectors.toList());
        List<Node> remainingNodes = getExpiredNodes(defaultExpiry);
        remainingNodes.removeAll(containerNodes);
        recycle(containerNodes);
        recycle(remainingNodes);
    }

    /** Get failed nodes that have expired according to given expiry */
    private List<Node> getExpiredNodes(Duration expiry) {
        return nodeRepository.getNodes(Node.State.failed).stream()
                             .filter(node -> node.history().event(History.Event.Type.failed)
                                                 .map(event -> event.at().plus(expiry).isBefore(clock.instant()))
                                                 .orElse(false))
                             .collect(Collectors.toList());
    }

    /** Move eligible nodes to dirty. This may be a subset of the given nodes */
    private void recycle(List<Node> nodes) {
        List<Node> nodesToRecycle = new ArrayList<>();
        for (Node candidate : nodes) {
            if (hasHardwareIssue(candidate)) {
                List<String> unparkedChildren = !candidate.type().isDockerHost() ? Collections.emptyList() :
                        nodeRepository.getChildNodes(candidate.hostname()).stream()
                                .filter(node -> node.state() != Node.State.parked)
                                .map(Node::hostname)
                                .collect(Collectors.toList());

                if (unparkedChildren.isEmpty()) {
                    nodeRepository.park(candidate.hostname(), Agent.system,
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
        nodeRepository.setDirty(nodesToRecycle, Agent.system, "Expired by FailedExpirer");
    }

    /** Returns whether the current node fail count should be used as an indicator of hardware issue */
    private boolean failCountIndicatesHardwareIssue(Node node) {
        if (node.flavor().getType() == Flavor.Type.DOCKER_CONTAINER) return false;
        return (zone.environment() == Environment.prod || zone.environment() == Environment.staging) &&
               node.status().failCount() >= maxAllowedFailures;
    }

    /** Returns whether node has any kind of hardware issue */
    private static boolean hasHardwareIssue(Node node) {
        return node.status().hardwareFailureDescription().isPresent() ||
               node.status().hardwareDivergence().isPresent();
    }

}
