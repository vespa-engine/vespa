// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <algorithm>

namespace proton::matching {

/**
 * This class is used for all calculations related to limiting the
 * number of results produced during matching based on the 'max-hits'
 * configuration in the 'match-phase' part of the rank profile in the
 * search definition.
 **/
class MatchPhaseLimitCalculator
{
private:
    const size_t _max_hits;
    const size_t _min_groups;
    const size_t _sample_hits;

public:
    /**
     * @param max_hits the number of hits you want
     * @param min_groups the minimum number of diversity groups you want
     * @param sample fraction of max_hits to be used as sample size before performing match phase limiting
     */
    MatchPhaseLimitCalculator(size_t max_hits, size_t min_groups, double sample) :
        _max_hits(max_hits),
        _min_groups(std::max(size_t(1), min_groups)),
        _sample_hits(max_hits * sample)
    {}
    size_t sample_hits_per_thread(size_t num_threads) const {
        return std::max(size_t(1), std::max(128 / num_threads, _sample_hits / num_threads));
    }
    size_t wanted_num_docs(double hit_rate) const {
        return std::min((double)0x7fffFFFF, std::max(128.0, _max_hits / hit_rate));
    }
    size_t estimated_hits(double hit_rate, size_t num_docs) const {
        return (size_t) (hit_rate * num_docs);
    }
    size_t max_group_size(size_t wanted_num_docs_in) const {
        return (wanted_num_docs_in / _min_groups);
    }
};

}
