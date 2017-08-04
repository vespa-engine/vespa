// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    typedef wand::score_t score_t;
    typedef wand::docid_t docid_t;

    /**
     * Params used to tweak the behavior of the WAND algorithm.
     */
    struct MatchParams
    {
        WeakAndHeap &scores;
        score_t      scoreThreshold;
        double       thresholdBoostFactor;
        uint32_t     scoresAdjustFrequency;
        docid_t      docIdLimit;
        MatchParams(WeakAndHeap &scores_,
                    score_t scoreThreshold_,
                    double thresholdBoostFactor_,
                    uint32_t scoresAdjustFrequency_)
            : scores(scores_),
              scoreThreshold(scoreThreshold_),
              thresholdBoostFactor(thresholdBoostFactor_),
              scoresAdjustFrequency(scoresAdjustFrequency_),
              docIdLimit(0)
        {}
        MatchParams &setDocIdLimit(docid_t value) {
            docIdLimit = value;
            return *this;
        }
    };

    /**
     * Params used for rank calculation.
     */
    struct RankParams
    {
        fef::TermFieldMatchData &rootMatchData;
        fef::MatchData::UP       childrenMatchData;
        RankParams(fef::TermFieldMatchData &rootMatchData_,
                   fef::MatchData::UP &&childrenMatchData_)
            : rootMatchData(rootMatchData_),
              childrenMatchData(std::move(childrenMatchData_))
        {}
    };

    typedef wand::Terms Terms;

    virtual size_t get_num_terms() const = 0;
    virtual int32_t get_term_weight(size_t idx) const = 0;
    virtual score_t get_max_score(size_t idx) const = 0;
    virtual const MatchParams &getMatchParams() const = 0;

    static SearchIterator *createArrayWand(const Terms &terms, const MatchParams &matchParams, RankParams &&rankParams, bool strict);
    static SearchIterator *createHeapWand(const Terms &terms, const MatchParams &matchParams, RankParams &&rankParams, bool strict);
    static SearchIterator *create(const Terms &terms, const MatchParams &matchParams, RankParams &&rankParams, bool strict);

    static SearchIterator::UP create(search::fef::TermFieldMatchData &tmd,
                                     const MatchParams &matchParams,
                                     const std::vector<int32_t> &weights,
                                     const std::vector<IDocumentWeightAttribute::LookupResult> &dict_entries,
                                     const IDocumentWeightAttribute &attr,
                                     bool strict);
};

}
