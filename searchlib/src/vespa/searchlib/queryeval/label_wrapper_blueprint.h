// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"

#include <vespa/searchlib/fef/handle.h>

namespace search::queryeval {

/**
 * Blueprint for a label wrapper: it matches exactly as its single child does,
 * and in addition exposes a constant score as the raw score of its own term
 * field handle, so that rank features such as itemRawScore can pick it up.
 *
 * The handle is allocated for the reserved "no field" (see fef::FieldInfo::no_field),
 * as the wrapper does not search any field of its own. This keeps it out of the
 * per-field rank features, which only ever address declared fields.
 */
class LabelWrapperBlueprint final : public IntermediateBlueprint {
private:
    fef::TermFieldHandle _handle;
    double               _score;

    AnyFlow my_flow(InFlow in_flow) const override;

public:
    LabelWrapperBlueprint(fef::TermFieldHandle handle, double score) noexcept;
    ~LabelWrapperBlueprint() override;

    FlowStats calculate_flow_stats(uint32_t docid_limit) const final;
    HitEstimate combine(const std::vector<HitEstimate>& data) const override;
    FieldSpecBaseList exposeFields() const override;
    bool always_needs_unpack() const override;
    void sort(Children& children, InFlow in_flow) const override;
    SearchIterator::UP createIntermediateSearch(MultiSearch::Children subSearches, fef::MatchData& md) const override;
    SearchIterator::UP createFilterSearchImpl(FilterConstraint constraint) const override;
    uint8_t calculate_cost_tier() const override;
};

} // namespace search::queryeval
