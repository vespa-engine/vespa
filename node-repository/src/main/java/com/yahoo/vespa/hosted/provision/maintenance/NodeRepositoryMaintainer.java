// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.concurrent.maintenance.JobMetrics;
import com.yahoo.concurrent.maintenance.Maintainer;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

/**
 * A maintainer is some job which runs at a fixed rate to perform some maintenance task on the node repo.
 *
 * @author bratseth
 */
public abstract class NodeRepositoryMaintainer extends Maintainer {

    private final NodeRepository nodeRepository;

    public NodeRepositoryMaintainer(NodeRepository nodeRepository, Duration interval, Metric metric) {
        super(null, interval, nodeRepository.clock(), nodeRepository.jobControl(),
              new NodeRepositoryJobMetrics(metric), nodeRepository.database().cluster(), true);
        this.nodeRepository = nodeRepository;
    }

    protected static Duration min(Duration a, Duration b) {
        return a.toMillis() < b.toMillis() ? a : b;
    }

    /** Returns the node repository */
    protected NodeRepository nodeRepository() { return nodeRepository; }

    /** Returns the node repository clock */
    protected Clock clock() { return nodeRepository.clock(); }

    /** A utility to group active tenant nodes by application */
    protected Map<ApplicationId, NodeList> activeNodesByApplication() {
        return nodeRepository().nodes()
                               .list(Node.State.active)
                               .nodeType(NodeType.tenant)
                               .not().tester()
                               .groupingBy(node -> node.allocation().get().owner());
    }

    private static class NodeRepositoryJobMetrics extends JobMetrics {

        private final Metric metric;

        public NodeRepositoryJobMetrics(Metric metric) {
            this.metric = metric;
        }

        @Override
        public void completed(String job, double successFactor, long duration) {
            var context = metric.createContext(Map.of("job", job));
            metric.set("maintenance.successFactorDeviation", successFactor, context);
            metric.set("maintenance.duration", duration, context);
        }

    }

}
