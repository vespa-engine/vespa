// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "matching_stats.h"

namespace proton {
namespace matching {

namespace {

MatchingStats::Partition &get_writable_partition(std::vector<MatchingStats::Partition> &state, size_t id) {
    if (state.size() <= id) {
        state.resize(id + 1);
    }
    return state[id];
}

} // namespace proton::matching::<unnamed>

MatchingStats &
MatchingStats::merge_partition(const Partition &partition, size_t id)
{
    get_writable_partition(_partitions, id) = partition;

    _docsMatched += partition.docsMatched();
    _docsRanked += partition.docsRanked();
    _docsReRanked += partition.docsReRanked();
    if (partition.softDoomed()) {
        _softDoomed = 1;
    }

    return *this;
}

MatchingStats &
MatchingStats::add(const MatchingStats &rhs)
{

    _queries += rhs._queries;
    _limited_queries += rhs._limited_queries;

    _docsMatched += rhs._docsMatched;
    _docsRanked += rhs._docsRanked;
    _docsReRanked += rhs._docsReRanked;
    _softDoomed += rhs.softDoomed();

    _queryCollateralTime.add(rhs._queryCollateralTime);
    _queryLatency.add(rhs._queryLatency);
    _matchTime.add(rhs._matchTime);
    _groupingTime.add(rhs._groupingTime);
    _rerankTime.add(rhs._rerankTime);
    for (size_t id = 0; id < rhs.getNumPartitions(); ++id) {
        get_writable_partition(_partitions, id).add(rhs.getPartition(id));
    }
    return *this;
}

MatchingStats &
MatchingStats::updatesoftDoomFactor(double softLimit, double duration) {
    if (duration < softLimit) {
        _softDoomFactor += 0.01*(softLimit - duration)/softLimit;
    } else {
        _softDoomFactor += 0.02*(softLimit - duration)/softLimit;
    }
    return *this;
}

}
} // namespace searchcore
