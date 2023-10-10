// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bm_merge_stats.h"

namespace search::bmcluster {

BmMergeStats::BmMergeStats()
    : BmMergeStats(0u, 0u)
{
}

BmMergeStats::BmMergeStats(uint32_t active, uint32_t queued)
    : _active(active),
      _queued(queued)
{
}


BmMergeStats::~BmMergeStats() = default;

BmMergeStats&
BmMergeStats::operator+=(const BmMergeStats& rhs)
{
    _active += rhs._active;
    _queued += rhs._queued;
    return *this;
}

bool
BmMergeStats::operator==(const BmMergeStats &rhs) const
{
    return ((_active == rhs._active) &&
            (_queued == rhs._queued));
}

}
