// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketgctimecalculator.h"

using vespalib::count_s;

namespace storage::distributor {

bool
BucketGcTimeCalculator::shouldGc(const document::BucketId& b,
                                 vespalib::duration currentTime,
                                 vespalib::duration lastRunAt) const
{
    if (count_s(_checkInterval) == 0) {
        return false;
    }
    std::chrono::seconds gcPoint(_hasher.hash(b) % count_s(_checkInterval));
    vespalib::duration currentPeriodStart(currentTime - (currentTime % _checkInterval));
    vespalib::duration newestValid(currentPeriodStart + gcPoint);

    // Should GC have been started in current period?
    if (currentTime >= newestValid && lastRunAt < newestValid) {
        return true;
    }
    // Not in current; did it miss the previous period?
    return lastRunAt < (newestValid - _checkInterval);
}

}
