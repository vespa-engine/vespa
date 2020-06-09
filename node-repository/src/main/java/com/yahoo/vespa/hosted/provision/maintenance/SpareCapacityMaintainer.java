// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Deployer;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A maintainer which attempts to ensure there is spare capacity available in chunks which can fit
 * all node resource configuration in use, such that the system is able to quickly replace a failed node
 * if necessary.
 *
 * This also emits the following metrics:
 * - Overcommitted hosts: Hosts whose capacity is less than the sum of its children's
 * - Spare host capacity, or how many hosts the repository can stand to lose without ending up in a situation where it's
 *   unable to find a new home for orphaned tenants.
 *
 * @author mgimle
 * @author bratseth
 */
public class SpareCapacityMaintainer extends NodeRepositoryMaintainer {

    private final Deployer deployer;
    private final Metric metric;
    private final Clock clock;

    public SpareCapacityMaintainer(Deployer deployer,
                                   NodeRepository nodeRepository,
                                   Metric metric,
                                   Clock clock,
                                   Duration interval) {
        super(nodeRepository, interval);
        this.deployer = deployer;
        this.metric = metric;
        this.clock = clock;
    }

    @Override
    protected void maintain() {
        if ( ! nodeRepository().zone().getCloud().allowHostSharing()) return;

        CapacityChecker capacityChecker = new CapacityChecker(nodeRepository());

        List<Node> overcommittedHosts = capacityChecker.findOvercommittedHosts();
        if (overcommittedHosts.size() != 0) {
            log.log(Level.WARNING, String.format("%d nodes are overcommitted! [ %s ]",
                                                 overcommittedHosts.size(),
                                                 overcommittedHosts.stream().map(Node::hostname).collect(Collectors.joining(", "))));
        }
        metric.set("overcommittedHosts", overcommittedHosts.size(), null);

        Optional<CapacityChecker.HostFailurePath> failurePath = capacityChecker.worstCaseHostLossLeadingToFailure();
        if (failurePath.isPresent()) {
            int worstCaseHostLoss = failurePath.get().hostsCausingFailure.size();
            metric.set("spareHostCapacity", worstCaseHostLoss - 1, null);
            if (worstCaseHostLoss <= 1) {
                Optional<Node> moveCandidate = identifyMoveCandidate(failurePath.get());
                if (moveCandidate.isPresent())
                    move(moveCandidate.get());
            }
        }
    }

    private Optional<Node> identifyMoveCandidate(CapacityChecker.HostFailurePath failurePath) {
        return Optional.empty();
    }

    private void move(Node node) {

    }

}
