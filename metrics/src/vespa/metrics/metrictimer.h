// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class MetricTimer
 * @ingruop metrics
 *
 * @brief Used to add time values to metrics.
 */

#pragma once

#include <vespa/fastos/fastos.h>
#include <vespa/metrics/valuemetric.h>
#include <chrono>

namespace metrics {

class MetricTimer
{
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
    uint64_t stop(ValueMetric<AvgVal, TotVal, SumOnAdd>& metric) {
        const auto delta = std::chrono::steady_clock::now() - _startTime;
        const uint64_t deltaMs(
                std::chrono::duration_cast<std::chrono::milliseconds>(delta)
                    .count());
        metric.addValue(deltaMs);
        return deltaMs;
    }

private:
    std::chrono::time_point<std::chrono::steady_clock> _startTime;
};

} // metrics

