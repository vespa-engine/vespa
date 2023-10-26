// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operation_rate_tracker.h"

namespace proton {

OperationRateTracker::OperationRateTracker(double rate_threshold)
    : _time_budget_per_op(vespalib::from_s(1.0 / rate_threshold)),
      _time_budget_window(std::max(vespalib::from_s(1.0), _time_budget_per_op)),
      _threshold_time()
{
}

void
OperationRateTracker::observe(vespalib::steady_time now)
{
    vespalib::steady_time cand_time = std::max(now - _time_budget_window, _threshold_time + _time_budget_per_op);
    _threshold_time = std::min(cand_time, now + _time_budget_window);
}

bool
OperationRateTracker::above_threshold(vespalib::steady_time now) const
{
    return _threshold_time > now;
}

void
OperationRateTracker::reset(vespalib::steady_time now)
{
    _threshold_time = now - _time_budget_window;
}

}
