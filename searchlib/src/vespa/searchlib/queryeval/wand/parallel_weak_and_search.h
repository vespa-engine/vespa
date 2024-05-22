// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "wand_parts.h"
#include "weak_and_heap.h"
#include <vespa/searchlib/queryeval/searchiterator.h>
#include <vespa/searchlib/fef/matchdata.h>
#include <vespa/searchlib/fef/termfieldmatchdata.h>

namespace search::queryeval {

/**
 * WAND search iterator that uses a shared heap between match threads.
 */
struct ParallelWeakAndSearch : public SearchIterator
{
    using score_t = wand::score_t;
    using docid_t = wand::docid_t;

    /**
     * Params used to tweak the behavior of the WAND algorithm.
     */
    struct MatchParams : wand::MatchParams
    {
        const double  thresholdBoostFactor;
        const docid_t docIdLimit;
        MatchParams(WeakAndHeap &scores_in,
                    score_t scoreThreshold_in,
                    double thresholdBoostFactor_in,
                    uint32_t scoresAdjustFrequency_in,
                    uint32_t docIdLimit_in) noexcept
            : wand::MatchParams(scores_in, scoreThreshold_in, scoresAdjustFrequency_in),
              thresholdBoostFactor(thresholdBoostFactor_in),
              docIdLimit(docIdLimit_in)
        {}
        MatchParams(WeakAndHeap &scores_in,
                    score_t scoreThreshold_in,
                    double thresholdBoostFactor_in,
                    uint32_t scoresAdjustFrequency_in) noexcept
            : MatchParams(scores_in, scoreThreshold_in, thresholdBoostFactor_in, scoresAdjustFrequency_in, 0)
        {}
    };

    /**
     * Params used for rank calculation.
     */
    struct RankParams
    {
        fef::TermFieldMatchData &rootMatchData;
        fef::MatchData::UP       childrenMatchData;
        RankParams(fef::TermFieldMatchData &rootMatchData_,
                   fef::MatchData::UP &&childrenMatchData_) noexcept
            : rootMatchData(rootMatchData_),
              childrenMatchData(std::move(childrenMatchData_))
        {}
    };

    using Terms = wand::Terms;

    virtual size_t get_num_terms() const = 0;
    virtual int32_t get_term_weight(size_t idx) const = 0;
    virtual score_t get_max_score(size_t idx) const = 0;
    virtual const MatchParams &getMatchParams() const = 0;

    static SearchIterator::UP createArrayWand(const Terms &terms, const MatchParams &matchParams, RankParams &&rankParams, bool strict);
    static SearchIterator::UP createHeapWand(const Terms &terms, const MatchParams &matchParams, RankParams &&rankParams, bool strict);
    static SearchIterator::UP create(const Terms &terms, const MatchParams &matchParams, RankParams &&rankParams, bool strict);

    static SearchIterator::UP create(fef::TermFieldMatchData &tmd, const MatchParams &matchParams,
                                     const std::vector<int32_t> &weights,
                                     const std::vector<IDirectPostingStore::LookupResult> &dict_entries,
                                     const IDocidWithWeightPostingStore &attr, bool strict);
};

}
