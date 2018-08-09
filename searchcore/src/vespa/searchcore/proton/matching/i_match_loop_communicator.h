// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/scores.h>
#include <utility>
#include <cstddef>
#include <cstdint>
#include <vector>

namespace proton::matching {

struct IMatchLoopCommunicator {
    using Range = search::queryeval::Scores;
    using RangePair = std::pair<Range, Range>;
    using Hit = std::pair<uint32_t, search::feature_t>;
    using Hits = std::vector<Hit>;
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
    virtual Hits selectBest(Hits sortedHits) = 0;
    virtual RangePair rangeCover(const RangePair &ranges) = 0;
    virtual ~IMatchLoopCommunicator() {}
};

}
