// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing;

import com.yahoo.documentapi.ProgressToken;
import com.yahoo.jdisc.Metric;

import java.time.Clock;
import java.util.Map;

import static ai.vespa.reindexing.Reindexing.State.SUCCESSFUL;

/**
 * Metrics for reindexing in a content cluster.
 *
 * @author jonmv
 */
class ReindexingMetrics {

    private final Metric metric;
    private final String cluster;

    ReindexingMetrics(Metric metric, String cluster) {
        this.metric = metric;
        this.cluster = cluster;
    }

    void dump(Reindexing reindexing) {
        reindexing.status().forEach((type, status) -> {
            metric.set("reindexing.progress",
                       status.progress().map(ProgressToken::percentFinished).map(percentage -> percentage * 1e-2)
                             .orElse(status.state() == SUCCESSFUL ? 1.0 : 0.0),
                       metric.createContext(Map.of("clusterid", cluster,
                                                   "documenttype", type.getName(),
                                                   "state", toString(status.state()))));
        });
    }

    private static String toString(Reindexing.State state) {
        switch (state) {
            case READY: return "ready";
            case RUNNING: return "running";
            case FAILED: return "failed";
            case SUCCESSFUL: return "successful";
            default: throw new IllegalArgumentException("Unknown reindexing state '" + state + "'");
        }
    }

}
