// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/scores.h>
#include <vespa/searchlib/queryeval/sorted_hit_sequence.h>
#include <utility>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace proton::matching {

struct IMatchLoopCommunicator {
    using Range = search::queryeval::Scores;
    using RangePair = std::pair<Range, Range>;
    using SortedHitSequence = search::queryeval::SortedHitSequence;
    using Hit = SortedHitSequence::Hit;
    using Hits = std::vector<Hit>;
    using TaggedHit = std::pair<Hit,size_t>;
    using TaggedHits = std::vector<TaggedHit>;
    struct Matches {
        size_t hits;
        size_t docs;
        Matches() : hits(0), docs(0) {}
        Matches(size_t hits_in, size_t docs_in) : hits(hits_in), docs(docs_in) {}
        void add(const Matches &rhs) {
            hits += rhs.hits;
            docs += rhs.docs;
        }
    };
    virtual double estimate_match_frequency(const Matches &matches) = 0;
    virtual TaggedHits get_second_phase_work(SortedHitSequence sortedHits, size_t thread_id) = 0;
    virtual std::pair<Hits,RangePair> complete_second_phase(TaggedHits my_results, size_t thread_id) = 0;
    virtual ~IMatchLoopCommunicator() {}
};

}
