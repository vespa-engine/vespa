// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include <thread>
#include <vespa/vespalib/stllike/string.h>
#include "counter.h"
#include "gauge.h"
#include "current_samples.h"
#include "snapshots.h"
#include "point.h"
#include "point_builder.h"
#include "dimension.h"
#include "label.h"

namespace vespalib::metrics {

/**
 * Interface for a Metrics manager, for creating metrics
 * and for fetching snapshots.
 **/
class MetricsManager
    : public std::enable_shared_from_this<MetricsManager>
{
public:
    virtual ~MetricsManager() {}

    /**
     * Get or create a counter metric.
     * @param name the name of the metric.
     **/
    virtual Counter counter(const vespalib::string &name, const vespalib::string &description) = 0;

    /**
     * Get or create a gauge metric.
     * @param name the name of the metric.
     **/
    virtual Gauge gauge(const vespalib::string &name, const vespalib::string &description) = 0;

    /**
     * Get or create a dimension for labeling metrics.
     * @param name the name of the dimension.
     **/
    virtual Dimension dimension(const vespalib::string &name) = 0; // get or create

    /**
     * Get or create a label.
     * @param value the label value.
     **/
    virtual Label label(const vespalib::string &value) = 0; // get or create

    /**
     * Create a PointBuilder for labeling metrics.
     **/
    PointBuilder pointBuilder() {
        return PointBuilder(shared_from_this());
    }

    /**
     * Create a PointBuilder for labeling metrics, starting with
     * an Point of already existing dimension/label pairs, which can
     * then be added to or changed.
     * @param from provide a Point to start from.
     *
     **/
    virtual PointBuilder pointBuilder(Point from) = 0;

    /**
     * Create a snapshot of sampled metrics (usually for the last minute).
     **/
    virtual Snapshot snapshot() = 0;

    /**
     * Create a snapshot of all sampled metrics the manager has seen.
     **/
    virtual Snapshot totalSnapshot() = 0;

    // for use from PointBuilder only
    virtual Point pointFrom(PointMap map) = 0;

    // for use from Counter only
    virtual void add(Counter::Increment inc) = 0;

    // for use from Gauge only
    virtual void sample(Gauge::Measurement value) = 0;
};


} // namespace vespalib::metrics
