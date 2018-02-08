// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matching_stats.h"

namespace proton::matching {

namespace {

MatchingStats::Partition &get_writable_partition(std::vector<MatchingStats::Partition> &state, size_t id) {
    if (state.size() <= id) {
        state.resize(id + 1);
    }
    return state[id];
}

} // namespace proton::matching::<unnamed>

MatchingStats::MatchingStats()
    : _queries(0),
      _limited_queries(0),
      _docidSpaceCovered(0),
      _docsMatched(0),
      _docsRanked(0),
      _docsReRanked(0),
      _softDoomed(0),
      _softDoomFactor(0.5),
      _queryCollateralTime(),
      _queryLatency(),
      _matchTime(),
      _groupingTime(),
      _rerankTime(),
      _partitions()
{ }

MatchingStats::~MatchingStats() = default;

MatchingStats &
MatchingStats::merge_partition(const Partition &partition, size_t id)
{
    get_writable_partition(_partitions, id) = partition;

    _docidSpaceCovered += partition.docsCovered();
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

    _docidSpaceCovered += rhs._docidSpaceCovered;
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
MatchingStats::updatesoftDoomFactor(double hardLimit, double softLimit, double duration) {
    if (duration < softLimit) {
        _softDoomFactor += 0.01*(softLimit - duration)/hardLimit;
    } else {
        _softDoomFactor += 0.02*(softLimit - duration)/hardLimit;
    }
    return *this;
}

}
