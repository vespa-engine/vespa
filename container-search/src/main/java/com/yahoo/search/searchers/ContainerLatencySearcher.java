// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchers;

import com.yahoo.component.chain.dependencies.After;
import com.yahoo.metrics.simple.Gauge;
import com.yahoo.metrics.simple.Point;
import com.yahoo.metrics.simple.PointBuilder;
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
    private Point dims = null;

    public ContainerLatencySearcher(MetricReceiver metrics) {
        latencyGauge = metrics.declareGauge("query_container_latency");
    }

    @Override
    public Result search(Query query, Execution execution) {
        if (dims == null) {
            PointBuilder p = latencyGauge.builder();
            p.set("chain", execution.chain().getId().stringValue());
            dims = p.build();
        }
        latencyGauge.sample(query.getDurationTime(), dims);
        return execution.search(query);
    }

}
