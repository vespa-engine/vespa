// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include <vespa/searchlib/fef/matchdatalayout.h>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

class SameElementSearch;

class SameElementBlueprint : public IntermediateBlueprint
{
private:
    FieldSpec             _field;
    bool                  _expensive;

    AnyFlow my_flow(InFlow in_flow) const override;
public:
    SameElementBlueprint(const FieldSpec &field, bool expensive);
    SameElementBlueprint(const SameElementBlueprint &) = delete;
    SameElementBlueprint &operator=(const SameElementBlueprint &) = delete;
    ~SameElementBlueprint() override;

    uint8_t calculate_cost_tier() const override;
    SearchIteratorUP createSearchImpl(search::fef::MatchData& md) const override;
    HitEstimate combine(const std::vector<HitEstimate>& data) const override;
    FieldSpecBaseList exposeFields() const override;
    void sort(Children& children, InFlow in_flow) const override;

    FlowStats calculate_flow_stats(uint32_t docid_limit) const override;
    bool always_needs_unpack() const override;

    std::unique_ptr<SameElementSearch> create_same_element_search(search::fef::MatchData& md,
                                                                  search::fef::TermFieldMatchData& tfmd) const;
    std::unique_ptr<SearchIterator> createIntermediateSearch(MultiSearch::Children sub_searches,
                                                             fef::MatchData& md) const override;

    SearchIteratorUP createFilterSearchImpl(FilterConstraint constraint) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    const std::string &field_name() const noexcept { return _field.getName(); }
    const FieldSpec& get_field() const noexcept { return _field; }
};

}
