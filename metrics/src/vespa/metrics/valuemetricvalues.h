// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class metrics::ValueMetric
 * @ingroup metrics
 *
 * @brief Creates a metric measuring any value.
 *
 * A value metric have the following properties:
 *   - Logs the average as a value event. (It is not strictly increasing)
 *   - When summing average metrics together, the sum becomes the average of
 *     all values added to both.
 */

#pragma once

#include "metricvalueset.h"
#include <limits>

namespace metrics {

template<typename AvgVal, typename TotVal>
struct ValueMetricValues : MetricValueClass {
    uint32_t _count;
    AvgVal _min, _max, _last;
    TotVal _total;

    struct AtomicImpl {
        AtomicImpl() noexcept
            : _count(0),
              _min(std::numeric_limits<AvgVal>::max()),
              _max(std::numeric_limits<AvgVal>::min()),
              _last(0),
              _total(0)
        {}
        AtomicImpl(const AtomicImpl & rhs) noexcept
                : _count(rhs._count.load(std::memory_order_relaxed)),
                  _min(rhs._min.load(std::memory_order_relaxed)),
                  _max(rhs._max.load(std::memory_order_relaxed)),
                  _last(rhs._last.load(std::memory_order_relaxed)),
                  _total(rhs._total.load(std::memory_order_relaxed))
        {}
        std::atomic<uint32_t> _count;
        std::atomic<AvgVal>   _min;
        std::atomic<AvgVal>   _max;
        std::atomic<AvgVal>   _last;
        std::atomic<TotVal>   _total;
    };

    ValueMetricValues();
    void relaxedStoreInto(AtomicImpl& target) const noexcept;
    void relaxedLoadFrom(const AtomicImpl& source) noexcept;

    template<typename T>
    T getValue(stringref id) const;
    double getDoubleValue(stringref id) const override;
    uint64_t getLongValue(stringref id) const override;
    void output(const std::string& id, std::ostream& out) const override;
    void output(const std::string& id, vespalib::JsonStream& stream) const override;
    template<typename A, typename T>
    friend std::ostream & operator << (std::ostream & os, const ValueMetricValues<A, T> & v);
};

} // metrics

