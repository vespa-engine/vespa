// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class MetricTimer
 * @ingruop metrics
 *
 * @brief Used to add time values to metrics.
 */

#pragma once

#include "valuemetric.h"
#include <chrono>

namespace metrics {

class MetricTimer {
public:
    // Start time point set by system steady clock
    MetricTimer() noexcept;
    // Start time point explicitly given
    explicit MetricTimer(std::chrono::steady_clock::time_point start_time) noexcept;

    template<typename AvgVal, typename TotVal, bool SumOnAdd>
    AvgVal stop(std::chrono::steady_clock::time_point now, ValueMetric<AvgVal, TotVal, SumOnAdd>& metric) const {
        const auto delta = now - _startTime;
        using ToDuration = std::chrono::duration<AvgVal, std::milli>;
        const auto deltaMs(std::chrono::duration_cast<ToDuration>(delta).count());
        metric.addValue(deltaMs);
        return deltaMs;
    }

    /**
     * Adds ms passed since this timer was constructed to given value metric.
     * Returns that value as well.
     *
     * Uses a steady (monotonic) clock internally so value should never
     * underflow or be affected by system clock changes.
     */
    template<typename AvgVal, typename TotVal, bool SumOnAdd>
    AvgVal stop(ValueMetric<AvgVal, TotVal, SumOnAdd>& metric) const {
        return stop(std::chrono::steady_clock::now(), metric);
    }

    [[nodiscard]] std::chrono::steady_clock::time_point start_time() const noexcept {
        return _startTime;
    }

private:
    std::chrono::steady_clock::time_point _startTime;
};

} // metrics

