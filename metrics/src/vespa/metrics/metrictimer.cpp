// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/metrics/metrictimer.h>

namespace metrics {

MetricTimer::MetricTimer() noexcept
    : _startTime(std::chrono::steady_clock::now())
{
    // Amusingly enough, steady_clock was not actually steady by default on
    // GCC < 4.8.1, so add a bit of compile-time paranoia just to make sure.
    static_assert(std::chrono::steady_clock::is_steady,
                  "Old/broken STL implementation; steady_clock not steady");
}

MetricTimer::MetricTimer(std::chrono::steady_clock::time_point start_time) noexcept
    : _startTime(start_time)
{
}

} // metrics
