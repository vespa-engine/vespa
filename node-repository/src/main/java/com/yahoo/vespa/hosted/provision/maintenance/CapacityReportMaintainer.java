// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Duration;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import java.util.*;

/**
 * Performs analysis on the node repository to produce metrics that pertain to the capacity of the node repository.
 * These metrics include:
 * Spare host capacity, or how many hosts the repository can stand to lose without ending up in a situation where it's
 * unable to find a new home for orphaned tenants.
 * Overcommitted hosts, which tracks if there are any hosts whose capacity is less than the sum of its children's.
 *
 * @author mgimle
 */
public class CapacityReportMaintainer extends Maintainer {

    private final Metric metric;
    private final NodeRepository nodeRepository;
    private static final Logger log = Logger.getLogger(CapacityReportMaintainer.class.getName());

    CapacityReportMaintainer(NodeRepository nodeRepository,
                             Metric metric,
                             Duration interval) {
        super(nodeRepository, interval);
        this.nodeRepository = nodeRepository;
        this.metric = Objects.requireNonNull(metric);
    }

    @Override
    protected void maintain() {
        if (nodeRepository.zone().cloud().value().equals("aws")) return; // Hosts and nodes are 1-1

        CapacityChecker capacityChecker = new CapacityChecker(this.nodeRepository);
        List<Node> overcommittedHosts = capacityChecker.findOvercommittedHosts();
        if (overcommittedHosts.size() != 0) {
            log.log(LogLevel.WARNING, String.format("%d nodes are overcommitted! [ %s ]", overcommittedHosts.size(),
                                                    overcommittedHosts.stream().map(Node::hostname).collect(Collectors.joining(", "))));
        }
        metric.set("overcommittedHosts", overcommittedHosts.size(), null);

        Optional<CapacityChecker.HostFailurePath> failurePath = capacityChecker.worstCaseHostLossLeadingToFailure();
        if (failurePath.isPresent()) {
            int worstCaseHostLoss = failurePath.get().hostsCausingFailure.size();
            metric.set("spareHostCapacity", worstCaseHostLoss - 1, null);
        }
    }

}
