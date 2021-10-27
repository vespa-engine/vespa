// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <cstdint>

namespace proton::matching {

/**
 * Numeric matching parameters. Some of these comes from the config,
 * others from the request.
 **/
struct MatchParams {
    const uint32_t          numDocs;
    const uint32_t          heapSize;
    const uint32_t          arraySize;
    const uint32_t          offset;
    const uint32_t          hits;
    const search::feature_t rankDropLimit;

    MatchParams(uint32_t          numDocs_in,
                uint32_t          heapSize_in,
                uint32_t          arraySize_in,
                search::feature_t rankDropLimit_in,
                uint32_t          offset_in,
                uint32_t          hits_in,
                bool              hasFinalRank,
                bool              needRanking=true);
    bool save_rank_scores() const { return ((heapSize + arraySize) != 0); }
    bool has_rank_drop_limit() const;
};

}
