// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    MetricTimer();

    /**
     * Adds ms passed since this timer was constructed to given value metric.
     * Returns that value as well.
     *
     * Uses a steady (monotonic) clock internally so value should never
     * underflow or be affected by system clock changes.
     */
    template<typename AvgVal, typename TotVal, bool SumOnAdd>
    AvgVal stop(ValueMetric<AvgVal, TotVal, SumOnAdd>& metric) const {
        const auto delta = std::chrono::steady_clock::now() - _startTime;
        using ToDuration = std::chrono::duration<AvgVal, std::milli>;
        const auto deltaMs(std::chrono::duration_cast<ToDuration>(delta).count());
        metric.addValue(deltaMs);
        return deltaMs;
    }

private:
    std::chrono::steady_clock::time_point _startTime;
};

} // metrics

