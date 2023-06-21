// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.component.chain.dependencies.After;
import com.yahoo.metrics.simple.Gauge;
import com.yahoo.metrics.simple.Point;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;

/**
 * Measure latency in container before query is sent to backend
 *
 * @author Arne H Juul
 */
@After(PhaseNames.BACKEND)
public class ContainerLatencySearcher extends Searcher {
    private final Gauge latencyGauge;

    public ContainerLatencySearcher(MetricReceiver metrics) {
        latencyGauge = metrics.declareGauge(ContainerMetrics.QUERY_CONTAINER_LATENCY.baseName());
    }

    @Override
    public Result search(Query query, Execution execution) {
        Point dims = latencyGauge.builder()
                .set("chain", execution.chain().getId().stringValue())
                .build();
        latencyGauge.sample(query.getDurationTime(), dims);
        return execution.search(query);
    }

}
