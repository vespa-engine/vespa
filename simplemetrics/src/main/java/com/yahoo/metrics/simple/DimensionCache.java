// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics.simple;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Basically the persistence layer for metrics. Both CPU and memory hungry, but
 * it runs in its own little world.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
class DimensionCache {
    private final Map<String, LinkedHashMap<Point, UntypedMetric>> persistentData = new HashMap<>();
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
        for (Map.Entry<String, List<Entry<Point, UntypedMetric>>> metric : toDelete.getValuesByMetricName().entrySet()) {
            LinkedHashMap<Point, UntypedMetric> cachedPoints = getCachedMetric(metric.getKey());

            for (Entry<Point, UntypedMetric> newestInterval : metric.getValue()) {
                // overwriting an existing entry does not update the order
                // in the map
                cachedPoints.remove(newestInterval.getKey());
                cachedPoints.put(newestInterval.getKey(), newestInterval.getValue());
            }
        }
    }

    private void padMetric(String metric,
            Bucket toPresent,
            int currentDataPoints) {
        final LinkedHashMap<Point, UntypedMetric>  cachedPoints = getCachedMetric(metric);
        int toAdd = pointsToKeep - currentDataPoints;
        @SuppressWarnings({"unchecked","rawtypes"})
            Entry<Point, UntypedMetric>[] cachedEntries = cachedPoints.entrySet().toArray(new Entry[0]);
        for (int i = cachedEntries.length - 1; i >= 0 && toAdd > 0; --i) {
            Entry<Point, UntypedMetric> leastOld = cachedEntries[i];
            final Identifier id = new Identifier(metric, leastOld.getKey());
            if (!toPresent.hasIdentifier(id)) {
                toPresent.put(id, leastOld.getValue().pruneData());
                --toAdd;
            }
        }
    }

    @SuppressWarnings("serial")
    private LinkedHashMap<Point, UntypedMetric> getCachedMetric(String metricName) {
        LinkedHashMap<Point, UntypedMetric> points = persistentData.get(metricName);
        if (points == null) {
            points = new LinkedHashMap<Point, UntypedMetric>(16, 0.75f, false) {
                protected boolean removeEldestEntry(Map.Entry<Point, UntypedMetric> eldest) {
                    return size() > pointsToKeep;
                }
            };
            persistentData.put(metricName, points);
        }
        return points;
    }

}
