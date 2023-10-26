// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::bmcluster {

/*
 * Class containing merge (throttler) stats for a service layer node.
 */
class BmMergeStats
{
    uint32_t _active;
    uint32_t _queued;
    
public:
    BmMergeStats();
    BmMergeStats(uint32_t active, uint32_t queued);
    ~BmMergeStats();
    BmMergeStats& operator+=(const BmMergeStats& rhs);
    bool operator==(const BmMergeStats &rhs) const;
    uint32_t get_active() const noexcept { return _active; }
    uint32_t get_queued() const noexcept { return _queued; }
};

}
