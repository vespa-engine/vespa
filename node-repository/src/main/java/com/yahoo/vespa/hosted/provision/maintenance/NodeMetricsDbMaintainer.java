// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.node.NodeMetrics;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.NodeMetricsDb;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.logging.Level;

/**
 * Maintainer which keeps the node metric db up to date by periodically fetching metrics from all
 * active nodes.
 */
public class NodeMetricsDbMaintainer extends Maintainer {

    private final NodeMetrics nodeMetrics;
    private final NodeMetricsDb nodeMetricsDb;

    public NodeMetricsDbMaintainer(NodeRepository nodeRepository,
                                   NodeMetrics nodeMetrics,
                                   NodeMetricsDb nodeMetricsDb,
                                   Duration interval) {
        super(nodeRepository, interval);
        this.nodeMetrics = nodeMetrics;
        this.nodeMetricsDb = nodeMetricsDb;
    }

    @Override
    protected void maintain() {
        for (Node node : nodeRepository().list().nodeType(NodeType.tenant).state(Node.State.active).asList()) {
            try {
                Collection<NodeMetrics.Metric> metrics = nodeMetrics.fetchMetrics(node.hostname());
                Instant timestamp = nodeRepository().clock().instant();
                metrics.forEach(metric -> nodeMetricsDb.update(metric.name(), metric.value(), node.hostname(), timestamp));
            }
            catch (Exception e) {
                log.log(Level.WARNING, "Could not fetch metrics from " + node, e); // TODO: Exclude allowed to be down nodes
            }
        }
    }

}
