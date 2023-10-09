// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "remove_operations_rate_tracker.h"
#include <vespa/vespalib/util/time.h>

namespace proton {

RemoveOperationsRateTracker::RemoveOperationsRateTracker(double remove_batch_rate_threshold,
                                                         double remove_rate_threshold)
    : _remove_batch_tracker(remove_batch_rate_threshold),
      _remove_tracker(remove_rate_threshold)
{
}

void
RemoveOperationsRateTracker::notify_remove_batch()
{
    _remove_batch_tracker.observe(vespalib::steady_clock::now());
}

void
RemoveOperationsRateTracker::notify_remove()
{
    _remove_tracker.observe(vespalib::steady_clock::now());
}

bool
RemoveOperationsRateTracker::remove_batch_above_threshold() const
{
    return _remove_batch_tracker.above_threshold(vespalib::steady_clock::now());
}

bool
RemoveOperationsRateTracker::remove_above_threshold() const
{
    return _remove_tracker.above_threshold(vespalib::steady_clock::now());
}

}
