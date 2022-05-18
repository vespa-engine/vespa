// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <memory>
#include "metric_id.h"
#include "point.h"

namespace vespalib {
namespace metrics {

class MetricsManager;
struct GaugeAggregator;

/**
 * Represents a gauge metric that can be measured.
 **/
class Gauge {
private:
    std::shared_ptr<MetricsManager> _manager;
    MetricId _id;
public:
    Gauge(std::shared_ptr<MetricsManager> m, MetricId id)
        : _manager(std::move(m)), _id(id)
    {}

    /**
     * Provide a sample for the gauge.
     * @param value the measurement for this sample
     * @param p the point representing labels for this sample (default empty)
     **/
    void sample(double value, Point p = Point::empty) const;

    // internal
    struct Measurement {
        using Key = std::pair<MetricId, Point>;
        Key idx;
        double value;
        Measurement() = delete;
        Measurement(Key k, double v) : idx(k), value(v) {}
    };

    typedef GaugeAggregator aggregator_type;
    typedef Measurement sample_type;
};

} // namespace vespalib::metrics
} // namespace vespalib
