// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/queryeval/scores.h>
#include <vespa/searchlib/queryeval/idiversifier.h>
#include <utility>
#include <cstddef>
#include <vector>

namespace proton::matching {

struct IMatchLoopCommunicator {
    using feature_t = search::feature_t;
    using Range = search::queryeval::Scores;
    using RangePair = std::pair<Range, Range>;
    using IndexesToKeep = std::vector<uint32_t>;
    using Hit = std::pair<uint32_t, feature_t>;
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
    virtual size_t selectBest(const std::vector<feature_t> &sortedScores) = 0;
    virtual IndexesToKeep selectDiversifiedBest(const std::vector<Hit> &sortedHits) = 0;
    virtual RangePair rangeCover(const RangePair &ranges) = 0;
    virtual ~IMatchLoopCommunicator() {}
};

}
