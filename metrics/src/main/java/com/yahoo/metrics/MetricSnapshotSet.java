// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

/**
 * Represents two snapshots for the same time period.
 */
public class MetricSnapshotSet {
    int count; // Number of times we need to join to building period
                 // before we have a full time window.
    int builderCount; // Number of times we've currently added to the
                        // building instance.
    MetricSnapshot current = null; // The last full period
    MetricSnapshot building = null; // The building period

    MetricSnapshotSet(String name, int period, int count, MetricSet source, boolean snapshotUnsetMetrics) {
        this.count = count;
        this.builderCount = 0;
        current = new MetricSnapshot(name, period, source, snapshotUnsetMetrics);
        current.reset(0);
        if (count != 1) {
            building = new MetricSnapshot(name, period, source, snapshotUnsetMetrics);
            building.reset(0);
        }
    }

    MetricSnapshot getNextTarget() {
        if (count == 1) {
            return current;
        } else {
            return building;
        }
    }

    public boolean haveCompletedNewPeriod(int newFromTime) {
        if (count == 1) {
            current.setToTime(newFromTime);
            return true;
        }
        building.setToTime(newFromTime);

        // If not time to roll yet, just return
        if (++builderCount < count) return false;
        // Building buffer done. Use that as current and reset current.
        MetricSnapshot tmp = current;
        current = building;
        building = tmp;
        building.setFromTime(newFromTime);
        building.setToTime(0);
        builderCount = 0;
        return true;
    }

    public boolean timeForAnotherSnapshot(int currentTime) {
        int lastTime = getFromTime() + builderCount * getPeriod();
        return currentTime >= lastTime + getPeriod();
    }

    public void reset(int currentTime) {
        if (count != 1) building.reset(currentTime);
        current.reset(currentTime);
        builderCount = 0;
    }

    public void recreateSnapshot(MetricSet metrics, boolean copyUnset) {
        if (count != 1) building.recreateSnapshot(metrics, copyUnset);
        current.recreateSnapshot(metrics, copyUnset);
    }

    public void setFromTime(int fromTime)
    {
        if (count != 1) {
            building.setFromTime(fromTime);
        } else {
            current.setFromTime(fromTime);
        }
    }

    public int getPeriod() {
        return current.getPeriod();
    }

    public int getFromTime() {
        return current.getFromTime();
    }

    public int getCount() {
        return count;
    }

    public MetricSnapshot getSnapshot() {
        return getSnapshot(false);
    }

    public MetricSnapshot getSnapshot(boolean getBuilding) {
        if (getBuilding) {
            if (count == 1) {
                throw new IllegalStateException("No temporary snapshot for set " + current.name);
            }
            return building;
        }

        return current;
    }

    public boolean hasTemporarySnapshot() {
        return count > 1;
    }

    public String getName() {
        return current.getName();
    }

    public int getBuilderCount() {
        return builderCount;
    }
}
