package com.yahoo.container.jdisc.metric;

import com.yahoo.jdisc.Metric;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
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

    public static final long REPORTING_INTERVAL = Duration.ofSeconds(62).toMillis();

    static class GcStats {
        private final long when;
        private final long count;
        private final long ms;

        private GcStats(long when, long count, long ms) {
            this.when = when;
            this.count = count;
            this.ms = ms;
        }
    }

    private Map<String, LinkedList<GcStats>> gcStatistics;

    private final Clock clock;

    public GarbageCollectionMetrics(Clock clock) {
        this.clock = clock;
        this.gcStatistics = new HashMap<>();
        collectGcStatistics(clock.millis());
    }

    private void collectGcStatistics(long now) {
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String gcName = gcBean.getName().replace(" ", "");
            GcStats stats = new GcStats(now, gcBean.getCollectionCount(), gcBean.getCollectionTime());

            LinkedList<GcStats> window = gcStatistics.computeIfAbsent(gcName, anyName -> new LinkedList<>());
            window.addLast(stats);
        }
    }

    private void cleanStatistics(long now) {
        long oldestToKeep = now - REPORTING_INTERVAL;

        for(Iterator<Map.Entry<String, LinkedList<GcStats>>> it = gcStatistics.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, LinkedList<GcStats>> entry = it.next();
            LinkedList<GcStats> history = entry.getValue();
            while(history.isEmpty() == false && history.getFirst().when < oldestToKeep) {
                history.removeFirst();
            }
            if(history.isEmpty()) {
                it.remove();
            }
        }
    }

    public void emitMetrics(Metric metric) {
        long now = clock.millis();

        collectGcStatistics(now);
        cleanStatistics(now);

        for (Map.Entry<String, LinkedList<GcStats>> item : gcStatistics.entrySet()) {
            GcStats reference = item.getValue().getFirst();
            GcStats latest = item.getValue().getLast();
            Map<String, String> contextData = new HashMap<>();
            contextData.put(DIMENSION_KEY, item.getKey());
            Metric.Context gcContext = metric.createContext(contextData);

            metric.set(GC_COUNT, latest.count - reference.count, gcContext);
            metric.set(GC_TIME, latest.ms - reference.ms, gcContext);
        }
    }

    // partial exposure for testing
    Map<String, LinkedList<GcStats>> getGcStatistics() {
        return gcStatistics;
    }
}
