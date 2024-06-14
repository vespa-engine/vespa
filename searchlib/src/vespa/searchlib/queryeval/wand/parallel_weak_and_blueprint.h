// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "wand_parts.h"
#include "weak_and_heap.h"
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/termfieldmatchdataarray.h>
#include <memory>
#include <vector>

namespace search::queryeval {

/**
 * Blueprint for the parallel weak and search operator.
 */
class ParallelWeakAndBlueprint : public ComplexLeafBlueprint
{
private:
    using score_t = wand::score_t;

    std::unique_ptr<WeakAndPriorityQueue> _scores;
    const wand::score_t                   _scoreThreshold;
    double                                _thresholdBoostFactor;
    const uint32_t                        _scoresAdjustFrequency;
    fef::MatchDataLayout                  _layout;
    std::vector<int32_t>                  _weights;
    std::vector<Blueprint::UP>            _terms;

public:
    ParallelWeakAndBlueprint(const ParallelWeakAndBlueprint &) = delete;
    ParallelWeakAndBlueprint &operator=(const ParallelWeakAndBlueprint &) = delete;
    ParallelWeakAndBlueprint(FieldSpecBase field, uint32_t scoresToTrack,
                             score_t scoreThreshold, double thresholdBoostFactor,
                             bool thread_safe);
    ~ParallelWeakAndBlueprint() override;

    const WeakAndHeap &getScores() const { return *_scores; }
    score_t getScoreThreshold() const { return _scoreThreshold; }
    double getThresholdBoostFactor() const { return _thresholdBoostFactor; }

    // Used by create visitor
    FieldSpecBase getNextChildField(FieldSpecBase parent) {
        return {parent.getFieldId(), _layout.allocTermField(parent.getFieldId()), false};
    }

    // Used by create visitor
    void reserve(size_t num_children);
    void addTerm(Blueprint::UP term, int32_t weight, HitEstimate & estimate);
    void complete(HitEstimate estimate) {
        setEstimate(estimate);
        set_tree_size(_terms.size() + 1);
    }

    void sort(InFlow in_flow) override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override;
    
    SearchIterator::UP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda) const override;
    std::unique_ptr<SearchIterator> createFilterSearch(FilterConstraint constraint) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(const ExecuteInfo &execInfo) override;
    bool always_needs_unpack() const override;
};

}
