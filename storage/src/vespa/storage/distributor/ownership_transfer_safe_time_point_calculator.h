// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/time.h>

namespace storage::distributor {

/**
 * When bucket ownership changes in a cluster, there exists a time period
 * where distributors, unless prevented to do so, may generate the same time
 * stamps as previous distributors. This may cause time stamp collisions
 * within buckets, which we do not have a good story for today.
 *
 * An ownership transfer waiter is a stop-gap solution to avoiding this
 * edge case. It assumes that, given a maximum expected clock skew in the
 * cluster, it is sufficient to wait until the (ceil(current time) + max skew)
 * time point has elapsed. Until this time point is reached, mutating external
 * feed operations that require timestamps will be bounced back to the client.
 *
 * This is a stop-gap solution in the sense that we later want to move to a
 * solution which is _aware_ of the maximum time stamp for any bucket owned
 * by this distributor and refuse to generate any operation with a time stamp
 * equal to or lower than this. The stop-gap also breaks down if, in fact,
 * the clock skew is higher than the expected one.
 *
 */
class OwnershipTransferSafeTimePointCalculator {
    std::chrono::seconds _max_cluster_clock_skew;
public:
    explicit OwnershipTransferSafeTimePointCalculator(std::chrono::seconds max_cluster_clock_skew)
        : _max_cluster_clock_skew(max_cluster_clock_skew)
    {
    }

    void setMaxClusterClockSkew(std::chrono::seconds sec) noexcept {
        _max_cluster_clock_skew = sec;
    }

    vespalib::system_time safeTimePoint(vespalib::system_time now) const;
};

}
