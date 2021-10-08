// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketgctimecalculator.h"

namespace storage::distributor {

bool
BucketGcTimeCalculator::shouldGc(const document::BucketId& b,
                                 std::chrono::seconds currentTime,
                                 std::chrono::seconds lastRunAt) const
{
    if (_checkInterval.count() == 0) {
        return false;
    }
    std::chrono::seconds gcPoint(_hasher.hash(b) % _checkInterval.count());
    std::chrono::seconds currentPeriodStart(currentTime
                                            - (currentTime % _checkInterval));
    std::chrono::seconds newestValid(currentPeriodStart + gcPoint);

    // Should GC have been started in current period?
    if (currentTime >= newestValid && lastRunAt < newestValid) {
        return true;
    }
    // Not in current; did it miss the previous period?
    return lastRunAt < (newestValid - _checkInterval);
}

}
