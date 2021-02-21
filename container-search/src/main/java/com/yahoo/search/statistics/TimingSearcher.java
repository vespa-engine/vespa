// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.statistics;

import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.dependencies.Before;
import com.yahoo.search.statistics.TimingSearcherConfig.Timer;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.cluster.PingableSearcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.statistics.TimeTracker.Activity;
import com.yahoo.statistics.Statistics;
import com.yahoo.statistics.Value;

/**
 * A searcher which is intended to be useful as a general probe for
 * measuring time consumption a search chain.
 *
 * @author Steinar Knutsen
 */
@Before("rawQuery")
public class TimingSearcher extends PingableSearcher {

    private Value measurements;
    private final boolean measurePing;
    private final boolean measureSearch;
    private final boolean measureFill;
    private static final Parameters defaultParameters = new Parameters(null, Activity.SEARCH);

    public static class Parameters {
        final String eventName;
        final Activity pathToSample;

        public Parameters(String eventName, Activity pathToSample) {
            super();
            this.eventName = eventName;
            this.pathToSample = pathToSample;
        }
    }

    TimingSearcher(ComponentId id, Parameters setUp, Statistics manager) {
        super(id);
        if (setUp == null) {
            setUp = defaultParameters;
        }
        String eventName = setUp.eventName;
        if (eventName == null || "".equals(eventName)) {
            eventName = id.getName();
        }
        measurements = new Value(eventName, manager, new Value.Parameters()
                .setNameExtension(true).setLogMax(true).setLogMin(true)
                .setLogMean(true).setLogSum(true).setLogInsertions(true)
                .setAppendChar('_'));

        measurePing = setUp.pathToSample == Activity.PING;
        measureSearch = setUp.pathToSample == Activity.SEARCH;
        measureFill = setUp.pathToSample == Activity.FILL;
    }

    public TimingSearcher(ComponentId id, TimingSearcherConfig config, Statistics manager) {
        this(id, buildParameters(config, id.getName()), manager);
    }

    private static Parameters buildParameters(
            TimingSearcherConfig config, String searcherName) {
        for (int i = 0; i < config.timer().size(); ++i) {
            Timer t = config.timer(i);
            if (t.name().equals(searcherName)) {
                return buildParameters(t);
            }
        }
        return null;
    }

    private static Parameters buildParameters(Timer t) {
        Activity m;
        Timer.Measure.Enum toSample = t.measure();
        if (toSample == Timer.Measure.FILL) {
            m = Activity.FILL;
        } else if (toSample == Timer.Measure.PING) {
            m = Activity.PING;
        } else {
            m = Activity.SEARCH;
        }
        return new Parameters(t.eventname(), m);
    }

    private long preMeasure(boolean doIt) {
        if (doIt) {
            return System.currentTimeMillis();
        } else {
            return 0L;
        }
    }

    private void postMeasure(boolean doIt, long start) {
        if (doIt) {
            long elapsed = System.currentTimeMillis() - start;
            measurements.put(elapsed);
        }
    }

    @Override
    public void fill(Result result, String summaryClass, Execution execution) {
        long start = preMeasure(measureFill);
        super.fill(result, summaryClass, execution);
        postMeasure(measureFill, start);
    }

    @Override
    public Pong ping(Ping ping, Execution execution) {
        long start = preMeasure(measurePing);
        Pong pong = execution.ping(ping);
        postMeasure(measurePing, start);
        return pong;
    }

    @Override
    public Result search(Query query, Execution execution) {
        long start = preMeasure(measureSearch);
        Result result = execution.search(query);
        postMeasure(measureSearch, start);
        return result;
    }

    /**
     * This method is only included for testing.
     */
    public void setMeasurements(Value measurements) {
        this.measurements = measurements;
    }

    @Override
    public void deconstruct() {
        // avoid dangling, duplicate loggers
        measurements.cancel();
        super.deconstruct();
    }

}
