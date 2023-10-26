// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * The persistence layer for metrics. Both CPU and memory hungry, but
 * it runs in its own little world.
 *
 * @author Steinar Knutsen
 */
class DimensionCache {

    private static class TimeStampedMetric {
       public final long millis;
       public final UntypedMetric metric;
       public TimeStampedMetric(long millis, UntypedMetric metric) {
           this.millis = millis;
           this.metric = metric;
       }
    }

    private final Map<String, LinkedHashMap<Point, TimeStampedMetric>> persistentData = new HashMap<>();
    private final int pointsToKeep;

    public DimensionCache(int pointsToKeep) {
        this.pointsToKeep = pointsToKeep;
    }

    void updateDimensionPersistence(Bucket toDelete, Bucket toPresent) {
        updatePersistentData(toDelete);
        padPresentation(toPresent);
    }

    private void padPresentation(Bucket toPresent) {
        Map<String, List<Entry<Point, UntypedMetric>>> currentMetricNames = toPresent.getValuesByMetricName();

        for (Map.Entry<String, List<Entry<Point, UntypedMetric>>> metric : currentMetricNames.entrySet()) {
            final int currentDataPoints = metric.getValue().size();
            if (currentDataPoints < pointsToKeep) {
                padMetric(metric.getKey(), toPresent, currentDataPoints);
            }
        }
        Set<String> keysMissingFromPresentation = new HashSet<>(persistentData.keySet());
        keysMissingFromPresentation.removeAll(currentMetricNames.keySet());
        for (String cachedMetric : keysMissingFromPresentation) {
            padMetric(cachedMetric, toPresent, 0);
        }
    }

    private void updatePersistentData(Bucket toDelete) {
        if (toDelete == null) {
            return;
        }
        long millis = toDelete.gotTimeStamps ? toDelete.toMillis : System.currentTimeMillis();
        for (Map.Entry<String, List<Entry<Point, UntypedMetric>>> metric : toDelete.getValuesByMetricName().entrySet()) {
            LinkedHashMap<Point, TimeStampedMetric> cachedPoints = getCachedMetric(metric.getKey());

            for (Entry<Point, UntypedMetric> newestInterval : metric.getValue()) {
                // overwriting an existing entry does not update the order
                // in the map
                cachedPoints.remove(newestInterval.getKey());
                TimeStampedMetric toInsert = new TimeStampedMetric(millis, newestInterval.getValue());
                cachedPoints.put(newestInterval.getKey(), toInsert);
            }
        }
    }

    private static final long MAX_AGE_MILLIS = 4 * 3600 * 1000;

    private void padMetric(String metric, Bucket toPresent, int currentDataPoints) {
        LinkedHashMap<Point, TimeStampedMetric> cachedPoints = getCachedMetric(metric);
        int toAdd = pointsToKeep - currentDataPoints;
        @SuppressWarnings({"unchecked","rawtypes"})
            Entry<Point, TimeStampedMetric>[] cachedEntries = cachedPoints.entrySet().toArray(new Entry[0]);
        long nowMillis = System.currentTimeMillis();
        for (int i = cachedEntries.length - 1; i >= 0 && toAdd > 0; --i) {
            Entry<Point, TimeStampedMetric> leastOld = cachedEntries[i];
            if (leastOld.getValue().millis + MAX_AGE_MILLIS  < nowMillis) {
                continue;
            }
            Identifier id = new Identifier(metric, leastOld.getKey());
            if ( ! toPresent.hasIdentifier(id)) {
                toPresent.put(id, leastOld.getValue().metric.pruneData());
                --toAdd;
            }
        }
    }

    @SuppressWarnings("serial")
    private LinkedHashMap<Point, TimeStampedMetric> getCachedMetric(String metricName) {
        LinkedHashMap<Point, TimeStampedMetric> points = persistentData.get(metricName);
        if (points == null) {
            points = new LinkedHashMap<>(16, 0.75f, false) {
                protected @Override boolean removeEldestEntry(Map.Entry<Point, TimeStampedMetric> eldest) {
                    return size() > pointsToKeep;
                }
            };
            persistentData.put(metricName, points);
        }
        return points;
    }

}
