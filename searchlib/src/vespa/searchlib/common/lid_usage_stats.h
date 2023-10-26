// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <chrono>
#include <stdint.h>

namespace search {

/**
 * Stats on the usage and availability of lids in a document meta store.
 */
class LidUsageStats {
public:
    using TimePoint = std::chrono::time_point<std::chrono::steady_clock>;

private:
    uint32_t _lidLimit;
    uint32_t _usedLids;
    uint32_t _lowestFreeLid;
    uint32_t _highestUsedLid;

public:
    LidUsageStats() noexcept
        : _lidLimit(0),
          _usedLids(0),
          _lowestFreeLid(0),
          _highestUsedLid(0)
    {
    }
    LidUsageStats(uint32_t lidLimit,
                  uint32_t usedLids,
                  uint32_t lowestFreeLid,
                  uint32_t highestUsedLid) noexcept
        : _lidLimit(lidLimit),
          _usedLids(usedLids),
          _lowestFreeLid(lowestFreeLid),
          _highestUsedLid(highestUsedLid)
    {
    }
    uint32_t getLidLimit() const { return _lidLimit; }
    uint32_t getUsedLids() const { return _usedLids; }
    uint32_t getLowestFreeLid() const { return _lowestFreeLid; }
    uint32_t getHighestUsedLid() const { return _highestUsedLid; }
    uint32_t getLidBloat() const {
        // Account for reserved lid 0
        int32_t lidBloat = getLidLimit() - getUsedLids() - 1;
        if (lidBloat < 0) {
            return 0u;
        }
        return lidBloat;
    }
    double getLidBloatFactor() const {
        return (double)getLidBloat() / (double)getLidLimit();
    }
    double getLidFragmentationFactor() const {
        int32_t freeLids = getHighestUsedLid() - getUsedLids();
        if (freeLids < 0) {
            return 0;
        }
        if (getHighestUsedLid() == 0) {
            return 0;
        }
        return (double)freeLids / (double)getHighestUsedLid();
    }
};

}

