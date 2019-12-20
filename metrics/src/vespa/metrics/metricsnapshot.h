// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

class MetricManager;

class MetricSnapshot
{
    Metric::String _name;
        // Period length of this snapshot
    uint32_t _period;
        // Time this snapshot was last updated.
    time_t _fromTime;
        // If set to 0, use _fromTime + _period.
    time_t _toTime;
        // Keeps the metrics set view of the snapshot
    std::unique_ptr<MetricSet> _snapshot;
        // Snapshots must own their own metrics
    mutable std::vector<Metric::UP> _metrics;

public:
    typedef std::unique_ptr<MetricSnapshot> UP;
    typedef std::shared_ptr<MetricSnapshot> SP;

    /** Create a fresh empty top level snapshot. */
    MetricSnapshot(const Metric::String& name);
    /** Create a snapshot of another metric source. */
    MetricSnapshot(const Metric::String& name, uint32_t period,
                   const MetricSet& source, bool copyUnset);
    virtual ~MetricSnapshot();

    void addToSnapshot(MetricSnapshot& other, bool reset_, time_t currentTime) {
        _snapshot->addToSnapshot(other.getMetrics(), other._metrics);
        if (reset_) reset(currentTime);
        other._toTime = currentTime;
    }
    void addToSnapshot(MetricSnapshot& other, time_t currentTime) const {
        _snapshot->addToSnapshot(other.getMetrics(), other._metrics);
        other._toTime = currentTime;
    }
    void setFromTime(time_t fromTime) { _fromTime = fromTime; }
    void setToTime(time_t toTime) { _toTime = toTime; }

    const Metric::String& getName() const { return _name; }
    uint32_t getPeriod() const { return _period; }
    time_t getFromTime() const { return _fromTime; }
    time_t getToTime() const { return _toTime; }
    time_t getLength() const
        { return (_toTime != 0 ? _toTime - _fromTime : _fromTime + _period); }
    const MetricSet& getMetrics() const { return *_snapshot; }
    MetricSet& getMetrics() { return *_snapshot; }
    void reset(time_t currentTime);
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
    MetricSnapshot::UP _current; // The last full period
    MetricSnapshot::UP _building; // The building period
public:
    typedef std::shared_ptr<MetricSnapshotSet> SP;

    MetricSnapshotSet(const Metric::String& name, uint32_t period,
                      uint32_t count, const MetricSet& source,
                      bool snapshotUnsetMetrics);

    const Metric::String& getName() const { return _current->getName(); }
    uint32_t getPeriod() const { return _current->getPeriod(); }
    time_t getFromTime() const { return _current->getFromTime(); }
    time_t getToTime() const { return _current->getToTime(); }
    uint32_t getCount() const { return _count; }
    uint32_t getBuilderCount() const { return _builderCount; }
    bool hasTemporarySnapshot() const { return (_building.get() != 0); }
    MetricSnapshot& getSnapshot(bool temporary = false)
        { return *((temporary && _count > 1) ? _building : _current); }
    const MetricSnapshot& getSnapshot(bool temporary = false) const
        { return *((temporary && _count > 1) ? _building : _current); }
    MetricSnapshot& getNextTarget();
    bool timeForAnotherSnapshot(time_t currentTime);
    bool haveCompletedNewPeriod(time_t newFromTime);
    void reset(time_t currentTime);
    /**
     * Recreate snapshot by cloning given metric set and then add the data
     * from the old one. New metrics have been added.
     */
    void recreateSnapshot(const MetricSet& metrics, bool copyUnset);
    void addMemoryUsage(MemoryConsumption&) const;
    void setFromTime(time_t fromTime);
};

} // metrics

