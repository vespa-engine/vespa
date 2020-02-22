// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetrics;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricsDb;
import com.yahoo.vespa.hosted.provision.autoscale.Resource;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.logging.Level;

/**
 * Maintainer which keeps the node metric db up to date by periodically fetching metrics from all
 * active nodes.
 */
public class NodeMetricsDbMaintainer extends Maintainer {

    private static final int maxWarningsPerInvocation = 2;

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
        int warnings = 0;
        for (Node node : nodeRepository().list().nodeType(NodeType.tenant).state(Node.State.active).asList()) {
            try {
                Collection<NodeMetrics.Metric> metrics = nodeMetrics.fetchMetrics(node.hostname());
                Instant timestamp = nodeRepository().clock().instant();
                metrics.forEach(metric -> nodeMetricsDb.add(node.hostname(), Resource.fromMetric(metric.name()), timestamp, metric.value()));
            }
            catch (Exception e) {
                if (warnings++ < maxWarningsPerInvocation)
                    log.log(Level.WARNING, "Could not update metrics from " + node, e); // TODO: Exclude allowed to be down nodes
            }
        }
        nodeMetricsDb.gc(nodeRepository().clock());
    }

}
