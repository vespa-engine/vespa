// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/feature.h>
#include <cstdint>
#include <optional>

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
    const std::optional<search::feature_t> first_phase_rank_score_drop_limit;

    MatchParams(uint32_t          numDocs_in,
                uint32_t          heapSize_in,
                uint32_t          arraySize_in,
                std::optional<search::feature_t> first_phase_rank_drop_limit_in,
                uint32_t          offset_in,
                uint32_t          hits_in,
                bool              hasFinalRank,
                bool              needRanking);
    bool save_rank_scores() const noexcept { return (arraySize != 0); }
};

}
