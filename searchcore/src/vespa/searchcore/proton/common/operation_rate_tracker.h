// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/time.h>

namespace proton {

/**
 * Tracks whether the rate (ops/sec) of an operation is above or below a given threshold.
 *
 * An operation is given a time budget which is the inverse of the rate threshold.
 * When we observe an operation that much time is "spent", and we adjust a threshold time accordingly.
 * If this time is into the future, the current observed rate is above the rate threshold.
 *
 * To avoid the threshold time racing into the future or lagging behind,
 * it is capped in both directions by a time budget window.
 */
class OperationRateTracker {
private:
    vespalib::duration _time_budget_per_op;
    vespalib::duration _time_budget_window;
    vespalib::steady_time _threshold_time;

public:
    OperationRateTracker(double rate_threshold);

    vespalib::duration get_time_budget_per_op() const { return _time_budget_per_op; }
    vespalib::duration get_time_budget_window() const { return _time_budget_window; }

    void observe(vespalib::steady_time now);
    bool above_threshold(vespalib::steady_time now) const;

    // Should only be used for testing
    void reset(vespalib::steady_time now);
};

}
