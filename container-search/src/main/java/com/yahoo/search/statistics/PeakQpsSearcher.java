// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.statistics;

import com.yahoo.collections.Tuple2;
import com.yahoo.concurrent.ThreadLocalDirectory;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.statistics.Callback;
import com.yahoo.statistics.Handle;
import com.yahoo.statistics.Statistics;
import com.yahoo.statistics.Value;

import java.util.*;

/**
 * Aggregate peak qps and expose through meta hits and/or log events.
 *
 * @author Steinar Knutsen
 */
public class PeakQpsSearcher extends Searcher {

    private final ThreadLocalDirectory<Deque<QueryRatePerSecond>, Long> directory;
    private final Value qpsStatistics;
    private final CompoundName propertyName;
    private final boolean useMetaHit;

    /**
     * Meta hit which carries the peak qps and mean qps since the last time this
     * data was requested. The URI is always "meta:qps". The data is stored as
     * Number subclasses in the fields named by the fields PEAK_QPS and MEAN_QPS
     * in the QpsHit class.
     */
    public static class QpsHit extends Hit {

        /** The name of the field containing mean QPS since the last measurement. */
        public static final String MEAN_QPS = "mean_qps";

        /** The name of the field containing peak QPS since the last measurement. */
        public static final String PEAK_QPS = "peak_qps";
        public static final String SCHEME = "meta";

        public QpsHit(Integer peakQps, Double meanQps) {
            super(SCHEME + ":qps");
            setField(PEAK_QPS, peakQps);
            setField(MEAN_QPS, meanQps);
        }

        public boolean isMeta() {
            return true;
        }

        @Override
        public String toString() {
            return "QPS hit: Peak QPS " + getField(PEAK_QPS) + ", mean QPS " + getField(MEAN_QPS) + ".";
        }

    }

    static class QueryRatePerSecond {
        long when;
        int howMany;

        QueryRatePerSecond(long when) {
            this.when = when;
            this.howMany = 0;
        }

        void add(int x) {
            howMany += x;
        }

        void increment() {
            howMany += 1;
        }

        @Override
        public String toString() {
            return "QueryRatePerSecond(" + when + ": " + howMany + ")";
        }
    }

    static class QueryRate implements
            ThreadLocalDirectory.Updater<Deque<QueryRatePerSecond>, Long> {
        @Override
        public Deque<QueryRatePerSecond> update(Deque<QueryRatePerSecond> current, Long when) {
            QueryRatePerSecond last = current.peekLast();
            if (last == null || last.when != when) {
                last = new QueryRatePerSecond(when);
                current.addLast(last);
            }
            last.increment();
            return current;
        }

        @Override
        public Deque<QueryRatePerSecond> createGenerationInstance(Deque<QueryRatePerSecond> previous) {
            if (previous == null) {
                return new ArrayDeque<>();
            } else {
                return new ArrayDeque<>(previous.size());
            }
        }
    }

    private class Fetcher implements Callback {
        @Override
        public void run(Handle h, boolean firstRun) {
            List<Deque<QueryRatePerSecond>> data = directory.fetch();
            List<QueryRatePerSecond> chewed = merge(data);
            for (QueryRatePerSecond qps : chewed) {
                qpsStatistics.put(qps.howMany);
            }
        }
    }

    public PeakQpsSearcher(MeasureQpsConfig config, Statistics manager) {
        directory = createDirectory();
        MeasureQpsConfig.Outputmethod.Enum method = config.outputmethod();
        if (method == MeasureQpsConfig.Outputmethod.METAHIT) {
            useMetaHit = true;
            propertyName = new CompoundName(config.queryproperty());
            qpsStatistics = null;
        } else if (method == MeasureQpsConfig.Outputmethod.STATISTICS) {
            String event = config.eventname();
            if (event == null || event.isEmpty()) {
                event = getId().getName();
                event = event.replace('.', '_');
            }
            qpsStatistics = new Value(event, manager, new Value.Parameters()
                    .setAppendChar('_').setLogMax(true).setLogMean(true)
                    .setLogMin(false).setLogRaw(false).setNameExtension(true)
                    .setCallback(new Fetcher()));
            useMetaHit = false;
            propertyName = null;
        } else {
            throw new IllegalStateException("Config definition out of sync with implementation." +
                                            " No way to create output for method " + method + ".");
        }
    }

    static ThreadLocalDirectory<Deque<QueryRatePerSecond>, Long> createDirectory() {
        return new ThreadLocalDirectory<>(new QueryRate());
    }

    static List<QueryRatePerSecond> merge(List<Deque<QueryRatePerSecond>> measurements) {
        List<QueryRatePerSecond> rates = new ArrayList<>();
        while (measurements.size() > 0) {
            Deque<Deque<QueryRatePerSecond>> consumeFrom = new ArrayDeque<>(measurements.size());
            long current = Long.MAX_VALUE;
            for (ListIterator<Deque<QueryRatePerSecond>> i = measurements.listIterator(); i.hasNext();) {
                Deque<QueryRatePerSecond> deck = i.next();
                if (deck.size() == 0) {
                    i.remove();
                    continue;
                }
                QueryRatePerSecond threadData = deck.peekFirst();
                if (threadData.when < current) {
                    consumeFrom.clear();
                    current = threadData.when;
                    consumeFrom.add(deck);
                } else if (threadData.when == current) {
                    consumeFrom.add(deck);
                }
            }
            if (consumeFrom.size() > 0) {
                rates.add(consume(consumeFrom));
            }
        }
        return rates;
    }

    private static QueryRatePerSecond consume(Deque<Deque<QueryRatePerSecond>> consumeFrom) {
        Deque<QueryRatePerSecond> valueQueue = consumeFrom.pop();
        QueryRatePerSecond value = valueQueue.pop();
        QueryRatePerSecond thisSecond = new QueryRatePerSecond(value.when);
        thisSecond.add(value.howMany);
        while (consumeFrom.size() > 0) {
            valueQueue = consumeFrom.pop();
            value = valueQueue.pop();
            thisSecond.add(value.howMany);
        }
        return thisSecond;

    }

    @Override
    public Result search(Query query, Execution execution) {
        Result r;
        long when = query.getStartTime() / 1000L;
        Hit meta = null;
        directory.update(when);
        if (useMetaHit) {
            if (query.properties().getBoolean(propertyName, false)) {
                List<QueryRatePerSecond> l = merge(directory.fetch());
                Tuple2<Integer, Double> maxAndMean = maxAndMean(l);
                meta = new QpsHit(maxAndMean.first, maxAndMean.second);
            }
        }
        r = execution.search(query);
        if (meta != null) {
            r.hits().add(meta);
        }
        return r;
    }

    private Tuple2<Integer, Double> maxAndMean(List<QueryRatePerSecond> l) {
        int max = Integer.MIN_VALUE;
        double sum = 0.0d;
        if (l.size() == 0) {
            return new Tuple2<>(0, 0.0);
        }
        for (QueryRatePerSecond qps : l) {
            sum += qps.howMany;
            if (qps.howMany > max) {
                max = qps.howMany;
            }
        }
        return new Tuple2<>(max, sum / (double) l.size());
    }

}
