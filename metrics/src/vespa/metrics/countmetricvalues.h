// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class CountMetric
 * \ingroup metrics
 *
 * \brief Metric representing a count.
 *
 * A counter metric have the following properties:
 *   - It can never decrease, only increase.
 *   - Logs its value as a count event.
 *   - When summing counts, the counts are added together.
 */

#pragma once

#include <vespa/metrics/metricvalueset.h>
#include <atomic>

namespace metrics {

template <typename T>
struct CountMetricValues : public MetricValueClass {
    T _value;

    struct AtomicImpl {
        AtomicImpl() noexcept : _value(0) {}
        AtomicImpl(const AtomicImpl & rhs) noexcept : _value(rhs._value.load(std::memory_order_relaxed)) {}
        std::atomic<T> _value;
    };

    void relaxedStoreInto(AtomicImpl& target) const noexcept {
        target._value.store(_value, std::memory_order_relaxed);
    }

    void relaxedLoadFrom(const AtomicImpl& source) noexcept {
        _value = source._value.load(std::memory_order_relaxed);
    }

    CountMetricValues() : _value(0) {}

    std::string toString() const;
    double getDoubleValue(stringref) const override;
    uint64_t getLongValue(stringref) const override;
    void output(const std::string&, std::ostream& out) const override;
    void output(const std::string&, vespalib::JsonStream& stream) const override;
    bool inUse() const { return (_value != 0); }
};

} // metrics
