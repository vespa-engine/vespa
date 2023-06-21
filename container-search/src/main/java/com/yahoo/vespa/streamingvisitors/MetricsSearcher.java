// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.streamingvisitors;

import com.yahoo.log.event.Event;
import com.yahoo.search.query.context.QueryContext;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.vdslib.VisitorStatistics;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import static com.yahoo.vespa.streamingvisitors.StreamingSearcher.STREAMING_STATISTICS;

/**
 * Generates mail-specific query metrics.
 */
public class MetricsSearcher extends Searcher {

    private static final CompoundName metricsearcherId = CompoundName.from("metricsearcher.id");
    private static final CompoundName streamingLoadtype = CompoundName.from("streaming.loadtype");

    private static final Logger log = Logger.getLogger(MetricsSearcher.class.getName());

    static class Stats {
        long latency = 0;
        int count = 0;
        int ok = 0;
        int failed = 0;
        long dataStreamed = 0;
        long documentsStreamed = 0;
    }

    Map<String, Stats> statMap = new TreeMap<>();
    private long lastMetricLog = 0;

    @Override
    public Result search(Query query, Execution execution) {
        long timeMs = System.currentTimeMillis();

        // Backwards compatibility - convert metricsearcher.id to streaming.loadtype
        // TODO Cleanup at some point
        String metricName = query.properties().getString(metricsearcherId);
        if (metricName != null) {
            query.properties().set(streamingLoadtype, metricName);
        }

        Result result = execution.search(query);

        long latency = System.currentTimeMillis() - timeMs;

        metricName = query.properties().getString(streamingLoadtype);
        if (metricName == null) {
            return result;
        }

        synchronized(this) {
            Stats stats = statMap.get(metricName);

            if (stats == null) {
                stats = new Stats();
                statMap.put(metricName, stats);
            }

            stats.count++;
            stats.latency += latency;

            if (result.hits().getError() != null &&
                !result.hits().getErrorHit().hasOnlyErrorCode(ErrorMessage.NULL_QUERY) &&
                !result.hits().getErrorHit().hasOnlyErrorCode(3)) {
                stats.failed++;
            } else {
                stats.ok++;
            }

            VisitorStatistics visitorstats = null;
            final QueryContext queryContext = query.getContext(false);
            if (queryContext != null) {
                visitorstats = (VisitorStatistics)queryContext.getProperty(STREAMING_STATISTICS);
            }
            if (visitorstats != null) {
                stats.dataStreamed += visitorstats.getBytesVisited();
                stats.documentsStreamed += visitorstats.getDocumentsVisited();
            } else {
                log.fine("No visitor statistics set in query! - don't use metrics searcher without streaming search");
            }

            if ((timeMs - lastMetricLog) > 60000) {
                for (Map.Entry<String, Stats> entry : statMap.entrySet()) {
                    stats = entry.getValue();
                    Event.value(entry.getKey() + "_latency", stats.count > 0 ? (double)stats.latency / (double)stats.count : 0);
                    Event.value(entry.getKey() + "_ok", stats.ok);
                    Event.value(entry.getKey() + "_failed", stats.failed);
                    Event.value(entry.getKey() + "_bytesstreamed", stats.dataStreamed);
                    Event.value(entry.getKey() + "_documentsstreamed", stats.documentsStreamed);

                    stats.latency = 0;
                    stats.count = 0;
                }

                lastMetricLog = timeMs;
            }
        }

        return result;
    }
}
