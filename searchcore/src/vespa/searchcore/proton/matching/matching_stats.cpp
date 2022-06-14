// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matching_stats.h"
#include <cmath>

namespace proton::matching {

namespace {

MatchingStats::Partition &get_writable_partition(std::vector<MatchingStats::Partition> &state, size_t id) {
    if (state.size() <= id) {
        state.resize(id + 1);
    }
    return state[id];
}

constexpr vespalib::duration MIN_TIMEOUT = 1ms;
constexpr double MAX_CHANGE_FACTOR = 5;

} // namespace proton::matching::<unnamed>

MatchingStats::MatchingStats(double prev_soft_doom_factor)
    : _queries(0),
      _limited_queries(0),
      _docidSpaceCovered(0),
      _docsMatched(0),
      _docsRanked(0),
      _docsReRanked(0),
      _softDoomed(0),
      _doomOvertime(),
      _softDoomFactor(prev_soft_doom_factor),
      _querySetupTime(),
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
    _doomOvertime.add(partition._doomOvertime);
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
    _doomOvertime.add(rhs._doomOvertime);

    _querySetupTime.add(rhs._querySetupTime);
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
MatchingStats::updatesoftDoomFactor(vespalib::duration hardLimit, vespalib::duration softLimit, vespalib::duration duration) {
    // The safety capping here should normally not be necessary as all input numbers
    // will normally be within reasonable values.
    // It is merely a safety measure to avoid overflow on bad input as can happen with time senstive stuff
    // in any soft real time system.
    if ((hardLimit >= MIN_TIMEOUT) && (softLimit >= MIN_TIMEOUT)) {
        double factor = softDoomFactor();
        double diff = vespalib::to_s(softLimit - duration)/vespalib::to_s(hardLimit);
        if (duration < softLimit) {
            // Since softdoom factor can become very small, allow a minimum change of some size
            diff = std::min(diff, factor*MAX_CHANGE_FACTOR);
            factor += 0.01*diff;
        } else {
            diff = std::max(diff, -factor*MAX_CHANGE_FACTOR);
            factor += 0.02*diff;
        }
        factor = std::max(factor, 0.01); // Never go below 1%
        softDoomFactor(factor);
    }
    return *this;
}

}
