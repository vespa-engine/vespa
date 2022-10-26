// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "wand_parts.h"
#include "weak_and_heap.h"
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <memory>
#include <vector>

namespace search::queryeval {

const uint32_t DEFAULT_PARALLEL_WAND_SCORES_ADJUST_FREQUENCY = 4;

/**
 * Blueprint for the parallel weak and search operator.
 */
class ParallelWeakAndBlueprint : public ComplexLeafBlueprint
{
private:
    typedef wand::score_t score_t;

    const FieldSpec                    _field;
    mutable SharedWeakAndPriorityQueue _scores;
    const wand::score_t                _scoreThreshold;
    double                             _thresholdBoostFactor;
    const uint32_t                     _scoresAdjustFrequency;
    HitEstimate                        _estimate;
    fef::MatchDataLayout               _layout;
    std::vector<int32_t>               _weights;
    std::vector<Blueprint::UP>         _terms;

    ParallelWeakAndBlueprint(const ParallelWeakAndBlueprint &);
    ParallelWeakAndBlueprint &operator=(const ParallelWeakAndBlueprint &);

public:
    ParallelWeakAndBlueprint(const FieldSpec &field,
                             uint32_t scoresToTrack,
                             score_t scoreThreshold,
                             double thresholdBoostFactor);
    ParallelWeakAndBlueprint(const FieldSpec &field,
                             uint32_t scoresToTrack,
                             score_t scoreThreshold,
                             double thresholdBoostFactor,
                             uint32_t scoresAdjustFrequency);
    virtual ~ParallelWeakAndBlueprint() override;

    const WeakAndHeap &getScores() const { return _scores; }

    score_t getScoreThreshold() const { return _scoreThreshold; }

    double getThresholdBoostFactor() const { return _thresholdBoostFactor; }

    // Used by create visitor
    FieldSpec getNextChildField(const FieldSpec &outer);

    // Used by create visitor
    void addTerm(Blueprint::UP term, int32_t weight);

    SearchIterator::UP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool strict) const override;
    std::unique_ptr<SearchIterator> createFilterSearch(bool strict, FilterConstraint constraint) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(const ExecuteInfo &execInfo) override;
    bool always_needs_unpack() const override;
};

}
