// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

MatchingStats::MatchingStats(double prev_soft_doom_factor) noexcept
    : _softDoomFactor(prev_soft_doom_factor),
      _partitions(),
      _stats(),
      _nn_exact_stats(),
      _nn_approx_stats()
{ }

MatchingStats::~MatchingStats() = default;

MatchingStats &
MatchingStats::merge_partition(const Partition &partition, size_t id)
{
    get_writable_partition(_partitions, id) = partition;

    _stats._docidSpaceCovered += partition.docsCovered();
    _stats._docsMatched += partition.docsMatched();
    _stats._docsRanked += partition.docsRanked();
    _stats._docsReRanked += partition.docsReRanked();
    _stats._distances_computed += partition.distances_computed();
    _stats._doomOvertime.add(partition._doomOvertime);
    if (partition.softDoomed()) {
        _stats._softDoomed = 1;
    }

    return *this;
}

MatchingStats &
MatchingStats::add(const MatchingStats &rhs) noexcept
{
    _stats.add(rhs._stats);

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
