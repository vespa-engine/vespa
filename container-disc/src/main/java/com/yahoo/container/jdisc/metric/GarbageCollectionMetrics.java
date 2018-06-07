package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.Metric;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

/**
 * @author ollivir
 */
public class GarbageCollectionMetrics {
    private static final String GC_PREFIX = "jdisc.gc.";
    private static final String GC_COUNT = GC_PREFIX + ".count";
    private static final String GC_TIME = GC_PREFIX + ".ms";
    private static final String DIMENSION_KEY = "gcName";

    public static final Duration REPORTING_INTERVAL = Duration.ofSeconds(62);

    static class GcStats {
        private final Instant when;
        private final long count;
        private final Duration totalRuntime;

        private GcStats(Instant when, long count, Duration totalRuntime) {
            this.when = when;
            this.count = count;
            this.totalRuntime = totalRuntime;
        }
    }

    private Map<String, LinkedList<GcStats>> gcStatistics;

    private final Clock clock;

    public GarbageCollectionMetrics(Clock clock) {
        this.clock = clock;
        this.gcStatistics = new HashMap<>();
        collectGcStatistics(clock.instant());
    }

    private void collectGcStatistics(Instant now) {
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String gcName = gcBean.getName().replace(" ", "");
            GcStats stats = new GcStats(now, gcBean.getCollectionCount(), Duration.ofMillis(gcBean.getCollectionTime()));

            LinkedList<GcStats> window = gcStatistics.computeIfAbsent(gcName, anyName -> new LinkedList<>());
            window.addLast(stats);
        }
    }

    private void cleanStatistics(Instant now) {
        Instant oldestToKeep = now.minus(REPORTING_INTERVAL);

        for(Iterator<Map.Entry<String, LinkedList<GcStats>>> it = gcStatistics.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, LinkedList<GcStats>> entry = it.next();
            LinkedList<GcStats> history = entry.getValue();
            while(history.isEmpty() == false && oldestToKeep.isAfter(history.getFirst().when)) {
                history.removeFirst();
            }
            if(history.isEmpty()) {
                it.remove();
            }
        }
    }

    public void emitMetrics(Metric metric) {
        Instant now = clock.instant();

        collectGcStatistics(now);
        cleanStatistics(now);

        for (Map.Entry<String, LinkedList<GcStats>> item : gcStatistics.entrySet()) {
            GcStats reference = item.getValue().getFirst();
            GcStats latest = item.getValue().getLast();
            Map<String, String> contextData = new HashMap<>();
            contextData.put(DIMENSION_KEY, item.getKey());
            Metric.Context gcContext = metric.createContext(contextData);

            metric.set(GC_COUNT, latest.count - reference.count, gcContext);
            metric.set(GC_TIME, latest.totalRuntime.minus(reference.totalRuntime).toMillis(), gcContext);
        }
    }

    // partial exposure for testing
    Map<String, LinkedList<GcStats>> getGcStatistics() {
        return gcStatistics;
    }
}
