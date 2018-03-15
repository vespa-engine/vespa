// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.statistics;

import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.concurrent.CopyOnWriteHashMap;
import com.yahoo.container.Server;
import com.yahoo.container.protect.Error;
import com.yahoo.jdisc.Metric;
import com.yahoo.log.LogLevel;
import com.yahoo.metrics.simple.MetricSettings;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorHit;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.statistics.Callback;
import com.yahoo.statistics.Counter;
import com.yahoo.statistics.Handle;
import com.yahoo.statistics.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

import static com.yahoo.container.protect.Error.*;


/**
 * <p>A searcher to gather statistics such as queries completed and query latency.  There
 * may be more than 1 StatisticsSearcher in the Searcher chain, each identified by a
 * Searcher ID.  The statistics accumulated by all StatisticsSearchers are stored
 * in the singleton StatisticsManager object. </p>
 * <p>
 * TODO: Fix events to handle more than one of these searchers properly.
 *
 * @author Gene Meyers
 * @author Steinar Knutsen
 * @author bergum
 */
@Before(PhaseNames.RAW_QUERY)
public class StatisticsSearcher extends Searcher {

    private static final CompoundName IGNORE_QUERY = new CompoundName("metrics.ignore");
    private static final String MAX_QUERY_LATENCY_METRIC = "max_query_latency";
    private static final String EMPTY_RESULTS_METRIC = "empty_results";
    private static final String HITS_PER_QUERY_METRIC = "hits_per_query";
    private static final String TOTALHITS_PER_QUERY_METRIC = "totalhits_per_query";
    private static final String FAILED_QUERIES_METRIC = "failed_queries";
    private static final String MEAN_QUERY_LATENCY_METRIC = "mean_query_latency";
    private static final String QUERY_LATENCY_METRIC = "query_latency";
    private static final String QUERIES_METRIC = "queries";
    private static final String ACTIVE_QUERIES_METRIC = "active_queries";
    private static final String PEAK_QPS_METRIC = "peak_qps";
    private static final String COVERAGE_METRIC = "coverage_per_query";
    private static final String DEGRADED_METRIC = "degraded_queries";

    private final Counter queries; // basic counter
    private final Counter failedQueries; // basic counter
    private final Counter nullQueries; // basic counter
    private final Counter illegalQueries; // basic counter
    private final Value queryLatency; // mean pr 5 min
    private final Value queryLatencyBuckets;
    private final Value maxQueryLatency; // separate to avoid name mangling
    @SuppressWarnings("unused") // all the work is done by the callback
    private final Value activeQueries; // raw measure every 5 minutes
    private final Value peakQPS; // peak 1s QPS
    private final Counter emptyResults; // number of results containing no concrete hits
    private final Value hitsPerQuery; // mean number of hits per query

    private final PeakQpsReporter peakQpsReporter;

    // Naming of enums are reflected directly in metric dimensions and should not be changed as they are public API
    private enum DegradedReason { match_phase, adaptive_timeout, timeout, non_ideal_state }

    private Metric metric;
    private Map<String, Metric.Context> chainContexts = new CopyOnWriteHashMap<>();
    private Map<String, Metric.Context> statePageOnlyContexts = new CopyOnWriteHashMap<>();
    private Map<String, Map<DegradedReason, Metric.Context>> degradedReasonContexts = new CopyOnWriteHashMap<>();
    private java.util.Timer scheduler = new java.util.Timer(true);

    // Callback to measure queries in flight every five minutes
    private class ActivitySampler implements Callback {
        public void run(Handle h, boolean firstRun) {
            if (firstRun) {
                metric.set(ACTIVE_QUERIES_METRIC, 0, null);
                return;
            }
            // TODO Server.get() is to be removed
            int searchQueriesInFlight = Server.get().searchQueriesInFlight();
            ((Value) h).put(searchQueriesInFlight);
            metric.set(ACTIVE_QUERIES_METRIC, searchQueriesInFlight, null);
        }
    }
    private class PeakQpsReporter extends java.util.TimerTask {
        private long prevMaxQPSTime = System.currentTimeMillis();
        private long queriesForQPS = 0;
        private Metric.Context metricContext = null;
        public void setContext(Metric.Context metricContext) {
            if (this.metricContext == null) {
                synchronized(this) {
                    this.metricContext = metricContext;
                }
            }
        }
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            synchronized (this) {
                if (metricContext == null) return;
                flushPeakQps(now);
            }
        }
        private void flushPeakQps(long now) {
            double ms = (double) (now - prevMaxQPSTime);
            final double value = ((double)queriesForQPS) / (ms / 1000.0);
            peakQPS.put(value);
            metric.set(PEAK_QPS_METRIC, value, metricContext);
            prevMaxQPSTime = now;
            queriesForQPS = 0;
        }
        void countQuery() {
            synchronized (this) {
                ++queriesForQPS;
            }
        }
    }

    public StatisticsSearcher(com.yahoo.statistics.Statistics manager, Metric metric, MetricReceiver metricReceiver) {
        this.peakQpsReporter = new PeakQpsReporter();
        this.metric = metric;

        queries = new Counter(QUERIES_METRIC, manager, false);
        failedQueries = new Counter(FAILED_QUERIES_METRIC, manager, false);
        nullQueries = new Counter("null_queries", manager, false);
        illegalQueries = new Counter("illegal_queries", manager, false);
        queryLatency = new Value(MEAN_QUERY_LATENCY_METRIC, manager, new Value.Parameters().setLogRaw(false).setLogMean(true).setNameExtension(false));
        maxQueryLatency = new Value(MAX_QUERY_LATENCY_METRIC, manager, new Value.Parameters().setLogRaw(false).setLogMax(true).setNameExtension(false));
        queryLatencyBuckets = Value.buildValue(QUERY_LATENCY_METRIC, manager, null);
        activeQueries = new Value(ACTIVE_QUERIES_METRIC, manager, new Value.Parameters().setLogRaw(true).setCallback(new ActivitySampler()));
        peakQPS = new Value(PEAK_QPS_METRIC, manager, new Value.Parameters().setLogRaw(false).setLogMax(true).setNameExtension(false));
        hitsPerQuery = new Value(HITS_PER_QUERY_METRIC, manager, new Value.Parameters().setLogRaw(false).setLogMean(true).setNameExtension(false));
        emptyResults = new Counter(EMPTY_RESULTS_METRIC, manager, false);
        metricReceiver.declareGauge(QUERY_LATENCY_METRIC, Optional.empty(), new MetricSettings.Builder().histogram(true).build());

        scheduler.schedule(peakQpsReporter, 1000, 1000);
    }

    @Override
    public void deconstruct() {
        scheduler.cancel();
    }

    private void qps(Metric.Context metricContext) {
        peakQpsReporter.setContext(metricContext);
        peakQpsReporter.countQuery();
    }

    private Metric.Context getChainMetricContext(String chainName) {
        Metric.Context context = chainContexts.get(chainName);
        if (context == null) {
            Map<String, String> dimensions = new HashMap<>();
            dimensions.put("chain", chainName);
            context = this.metric.createContext(dimensions);
            chainContexts.put(chainName, context);
        }
        return context;
    }

    private Metric.Context getDegradedMetricContext(String chainName, Coverage coverage) {
        Map<DegradedReason, Metric.Context> reasons = degradedReasonContexts.get(chainName);
        if (reasons == null) {
            reasons = new HashMap<>(4);
            for (DegradedReason reason : DegradedReason.values() ) {
                Map<String, String> dimensions = new HashMap<>();
                dimensions.put("chain", chainName);
                dimensions.put("reason", reason.toString());
                Metric.Context context = this.metric.createContext(dimensions);
                reasons.put(reason, context);
            }
            degradedReasonContexts.put(chainName, reasons);
        }
        if (coverage.isDegradedByMatchPhase()) {
            return reasons.get(DegradedReason.match_phase);
        }
        if (coverage.isDegradedByTimeout()) {
            return reasons.get(DegradedReason.timeout);
        }
        if (coverage.isDegradedByAdapativeTimeout()) {
            return reasons.get(DegradedReason.adaptive_timeout);
        }
        return reasons.get(DegradedReason.non_ideal_state);
    }

    /**
     * Generate statistics for the query passing through this Searcher
     * 1) Add 1 to total query count
     * 2) Add response time to total response time (time from entry to return)
     * 3) .....
     */
    public Result search(com.yahoo.search.Query query, Execution execution) {
        if (query.properties().getBoolean(IGNORE_QUERY,false)) {
            return execution.search(query);
        }

        Metric.Context metricContext = getChainMetricContext(execution.chain().getId().stringValue());

        incrQueryCount(metricContext);
        logQuery(query);
        long start = System.currentTimeMillis(); // Start time, in millisecs.
        qps(metricContext);
        Result result;
        //handle exceptions thrown below in searchers
        try {
            result = execution.search(query); // Pass on down the chain
        } catch (Exception  e) {
            incrErrorCount(null, metricContext);
            throw e;
        }

        long end = System.currentTimeMillis(); // Start time, in millisecs.
        long latency = end - start;
        if (latency >= 0) {
            addLatency(latency, metricContext);
        } else {
            getLogger().log(LogLevel.WARNING,
                            "Apparently negative latency measure, start: " + start
                            + ", end: " + end + ", for query: " + query.toString());
        }
        if (result.hits().getError() != null) {
            incrErrorCount(result, metricContext);
            incrementStatePageOnlyErrors(result);
        }
        Coverage queryCoverage = result.getCoverage(false);
        if (queryCoverage.isDegraded()) {
            Metric.Context degradedContext = getDegradedMetricContext(execution.chain().getId().stringValue(), queryCoverage);
            metric.add(DEGRADED_METRIC, 1, degradedContext);
        }
        int hitCount = result.getConcreteHitCount();
        hitsPerQuery.put((double) hitCount);
        metric.set(COVERAGE_METRIC, (double) queryCoverage.getResultPercentage(), metricContext);
        metric.set(HITS_PER_QUERY_METRIC, (double) hitCount, metricContext);
        metric.set(TOTALHITS_PER_QUERY_METRIC, (double) result.getTotalHitCount(), metricContext);
        if (hitCount == 0) {
            emptyResults.increment();
            metric.add(EMPTY_RESULTS_METRIC, 1, metricContext);
        }

        // Update running averages
        //setAverages();

        return result;
    }

    private void logQuery(com.yahoo.search.Query query) {
        // Don't parse the query if it's not necessary for the logging Query.toString triggers parsing
        if (getLogger().isLoggable(Level.FINER)) {
            getLogger().finer("Query: " + query.toString());
        }
    }

    private void addLatency(long latency, Metric.Context metricContext) {
        //myStats.addLatency(latency);
        queryLatency.put(latency);
        metric.set(QUERY_LATENCY_METRIC, latency, metricContext);
        metric.set(MEAN_QUERY_LATENCY_METRIC, latency, metricContext);
        maxQueryLatency.put(latency);
        metric.set(MAX_QUERY_LATENCY_METRIC, latency, metricContext);
        queryLatencyBuckets.put(latency);
    }

    private void incrQueryCount(Metric.Context metricContext) {
        //myStats.incrQueryCnt();
        queries.increment();
        metric.add(QUERIES_METRIC, 1, metricContext);
    }

    private void incrErrorCount(Result result, Metric.Context metricContext) {
        failedQueries.increment();
        metric.add(FAILED_QUERIES_METRIC, 1, metricContext);

        if (result == null) // the chain threw an exception
            metric.add("error.unhandled_exception", 1, metricContext);
        else if (result.hits().getErrorHit().hasOnlyErrorCode(Error.NULL_QUERY.code))
            nullQueries.increment();
        else if (result.hits().getErrorHit().hasOnlyErrorCode(Error.ILLEGAL_QUERY.code))
            illegalQueries.increment();
    }

    /**
     * Creates error metric for StateHandler only. These metrics are only exposed on /state/v1/metrics page
     * and not forwarded to the log file.
     *
     * @param result The result to check for errors
     */
    private void incrementStatePageOnlyErrors(Result result) {
        if (result == null) return;

        ErrorHit error = result.hits().getErrorHit();
        if (error == null) return;

        for (ErrorMessage m : error.errors()) {
            int code = m.getCode();
            Metric.Context c = getDimensions(m.getSource());
            if (code == TIMEOUT.code) {
                metric.add("error.timeout", 1, c);
            } else if (code == NO_BACKENDS_IN_SERVICE.code) {
                metric.add("error.backends_oos", 1, c);
            } else if (code == ERROR_IN_PLUGIN.code) {
                metric.add("error.plugin_failure", 1, c);
            } else if (code == BACKEND_COMMUNICATION_ERROR.code) {
                metric.add("error.backend_communication_error", 1, c);
            } else if (code == EMPTY_DOCUMENTS.code) {
                metric.add("error.empty_document_summaries", 1, c);
            } else if (code == ILLEGAL_QUERY.code) {
                metric.add("error.illegal_query", 1, c);
            } else if (code == INVALID_QUERY_PARAMETER.code) {
                metric.add("error.invalid_query_parameter", 1, c);
            } else if (code == INTERNAL_SERVER_ERROR.code) {
                metric.add("error.internal_server_error", 1, c);
            } else if (code == SERVER_IS_MISCONFIGURED.code) {
                metric.add("error.misconfigured_server", 1, c);
            } else if (code == INVALID_QUERY_TRANSFORMATION.code) {
                metric.add("error.invalid_query_transformation", 1, c);
            } else if (code == RESULT_HAS_ERRORS.code) {
                metric.add("error.result_with_errors", 1, c);
            } else if (code == UNSPECIFIED.code) {
                metric.add("error.unspecified", 1, c);
            }
        }
    }

    private Metric.Context getDimensions(String source) {
        Metric.Context context = statePageOnlyContexts.get(source == null ? "" : source);
        if (context == null) {
            Map<String, String> dims = new HashMap<>();
            if (source != null) {
                dims.put("source", source);
            }
            context = this.metric.createContext(dims);
            statePageOnlyContexts.put(source == null ? "" : source, context);
        }
        // TODO add other relevant metric dimensions
        // Would be nice to have chain as a dimension as
        // we can separate errors from different chains
        return context;
    }

}

