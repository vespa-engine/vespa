// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>
#include <chrono>
#include <memory>
#include <vespa/vespalib/stllike/string.h>

namespace vespalib {
namespace metrics {

using clock = std::chrono::steady_clock;

class MetricsCollector;

struct MetricIdentifier {
    const size_t name_idx;
    const size_t point_idx;

    MetricIdentifier() : name_idx(-1), point_idx(0) {}

    explicit MetricIdentifier(size_t id)
      : name_idx(id), point_idx(0) {}

    MetricIdentifier(size_t id, size_t pt)
      : name_idx(id), point_idx(pt) {}

    bool operator< (const MetricIdentifier &other) const {
        if (name_idx < other.name_idx) return true;
        if (name_idx == other.name_idx) {
            return (point_idx < other.point_idx);
        }
        return false;
    }
    bool operator== (const MetricIdentifier &other) const {
        return (name_idx == other.name_idx);
    }
};


class MergedCounter;
class CounterIncrement;

class Counter {
    std::shared_ptr<MetricsCollector> _manager;
    MetricIdentifier _idx;
public:
    Counter() : _manager(), _idx() {}
    Counter(const Counter&) = delete;
    Counter(Counter &&other) = default;
    Counter& operator= (const Counter &) = delete;
    Counter& operator= (Counter &&other) = default;
    Counter(std::shared_ptr<MetricsCollector> m, int idx) : _manager(m), _idx(idx) {}

    void add();
    void add(size_t count);

    MetricIdentifier id() const { return _idx; }

    typedef MergedCounter aggregator_type;
    typedef CounterIncrement sample_type;
};

class MergedGauge;
class GaugeMeasurement;

class Gauge {
private:
    std::shared_ptr<MetricsCollector> _manager;
    MetricIdentifier _idx;
public:
    Gauge(std::shared_ptr<MetricsCollector> m, int idx) : _manager(m), _idx(idx) {}
    void sample(double value);
    MetricIdentifier id() const { return _idx; }

    typedef MergedGauge aggregator_type;
    typedef GaugeMeasurement sample_type;
};

struct CounterIncrement {
    MetricIdentifier idx;
    size_t value;
    CounterIncrement() = delete;
    CounterIncrement(MetricIdentifier id, size_t v) : idx(id), value(v) {}
};

struct GaugeMeasurement {
    MetricIdentifier idx;
    double value;
    GaugeMeasurement() = delete;
    GaugeMeasurement(MetricIdentifier id, double v) : idx(id), value(v) {}
};

} // namespace vespalib::metrics
} // namespace vespalib

namespace std
{
    template<> struct hash<vespalib::metrics::MetricIdentifier>
    {
        typedef vespalib::metrics::MetricIdentifier argument_type;
        typedef std::size_t result_type;
        result_type operator()(argument_type const& ident) const noexcept
        {
            return (ident.point_idx << 20) + ident.name_idx;
        }
    };
}
