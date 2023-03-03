package com.yahoo.application.container.components;

import com.yahoo.metrics.simple.jdisc.JdiscMetricsFactory;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * @author bratseth
 */
public class ComponentWithMetrics extends Searcher {

    private final JdiscMetricsFactory metrics;

    public ComponentWithMetrics(JdiscMetricsFactory metrics) {
        this.metrics = metrics;
    }

    public JdiscMetricsFactory metrics() { return metrics; }

    @Override
    public Result search(Query query, Execution execution) {
        return execution.search(query);
    }

}
