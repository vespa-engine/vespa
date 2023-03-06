// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/**
 * \class metrics::MetricSnapshot
 * \ingroup metrics
 *
 * \brief Represents a snapshot of a metric set.
 */
#pragma once

#include <map>
#include <vespa/metrics/metric.h>
#include <vespa/metrics/metricset.h>
#include <vespa/vespalib/util/time.h>

namespace metrics {

using system_time = vespalib::system_time;

class MetricManager;

class MetricSnapshot
{
    Metric::String _name;
        // Period length of this snapshot
    system_time::duration _period;
        // Time this snapshot was last updated.
    system_time _fromTime;
        // If set to 0, use _fromTime + _period.
    system_time _toTime;
        // Keeps the metrics set view of the snapshot
    std::unique_ptr<MetricSet> _snapshot;
        // Snapshots must own their own metrics
    mutable std::vector<Metric::UP> _metrics;

public:
    /** Create a fresh empty top level snapshot. */
    MetricSnapshot(const Metric::String& name);
    /** Create a snapshot of another metric source. */
    MetricSnapshot(const Metric::String& name, system_time::duration period,
                   const MetricSet& source, bool copyUnset);
    ~MetricSnapshot();

    void addToSnapshot(MetricSnapshot& other, bool reset_, system_time currentTime) {
        _snapshot->addToSnapshot(other.getMetrics(), other._metrics);
        if (reset_) reset(currentTime);
        other._toTime = currentTime;
    }
    void addToSnapshot(MetricSnapshot& other, system_time currentTime) const {
        _snapshot->addToSnapshot(other.getMetrics(), other._metrics);
        other._toTime = currentTime;
    }
    void setFromTime(system_time fromTime) { _fromTime = fromTime; }
    void setToTime(system_time toTime) { _toTime = toTime; }

    const Metric::String& getName() const { return _name; }
    system_time::duration getPeriod() const { return _period; }
    system_time getFromTime() const { return _fromTime; }
    system_time getToTime() const { return _toTime; }
    const MetricSet& getMetrics() const { return *_snapshot; }
    MetricSet& getMetrics() { return *_snapshot; }
    void reset(system_time currentTime);
    void reset();
    /**
     * Recreate snapshot by cloning given metric set and then add the data
     * from the old one. New metrics have been added.
     */
    void recreateSnapshot(const MetricSet& metrics, bool copyUnset);

    void addMemoryUsage(MemoryConsumption&) const;
};

class MetricSnapshotSet {
    uint32_t _count; // Number of times we need to add to building period
                     // before we have a full time window.
    uint32_t _builderCount; // Number of times we've currently added to the
                            // building instance.
    std::unique_ptr<MetricSnapshot> _current; // The last full period
    std::unique_ptr<MetricSnapshot> _building; // The building period
public:
    MetricSnapshotSet(const Metric::String& name, system_time::duration period, uint32_t count,
                      const MetricSet& source, bool snapshotUnsetMetrics);

    const Metric::String& getName() const { return _current->getName(); }
    system_time::duration getPeriod() const { return _current->getPeriod(); }
    system_time getFromTime() const { return _current->getFromTime(); }
    system_time getToTime() const { return _current->getToTime(); }
    system_time getNextWorkTime() const { return getToTime() + getPeriod(); }
    uint32_t getCount() const { return _count; }
    uint32_t getBuilderCount() const { return _builderCount; }
    MetricSnapshot& getSnapshot() {
        return getSnapshot(false);
    }
    MetricSnapshot& getSnapshot(bool temporary) {
        return *((temporary && _count > 1) ? _building : _current);
    }
    const MetricSnapshot& getSnapshot() const {
        return getSnapshot(false);
    }
    const MetricSnapshot& getSnapshot(bool temporary) const {
        return *((temporary && _count > 1) ? _building : _current);
    }
    MetricSnapshot& getNextTarget();
    bool timeForAnotherSnapshot(system_time currentTime);
    bool haveCompletedNewPeriod(system_time newFromTime);
    void reset(system_time currentTime);
    /**
     * Recreate snapshot by cloning given metric set and then add the data
     * from the old one. New metrics have been added.
     */
    void recreateSnapshot(const MetricSet& metrics, bool copyUnset);
    void addMemoryUsage(MemoryConsumption&) const;
    void setFromTime(system_time fromTime);
};

} // metrics

