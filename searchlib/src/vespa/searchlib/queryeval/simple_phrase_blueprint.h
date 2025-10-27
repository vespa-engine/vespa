// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include <vespa/searchlib/fef/matchdatalayout.h>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

class SimplePhraseBlueprint : public ComplexLeafBlueprint
{
private:
    FieldSpec                  _field;
    HitEstimate                _estimate;
    std::vector<Blueprint::UP> _terms;

public:
    SimplePhraseBlueprint(const FieldSpec &field, bool expensive);
    SimplePhraseBlueprint(const SimplePhraseBlueprint &) = delete;
    SimplePhraseBlueprint &operator=(const SimplePhraseBlueprint &) = delete;

    ~SimplePhraseBlueprint() override;

    // used by create visitor
    static FieldSpec next_child_field(const FieldSpec &outer, fef::MatchDataLayout &layout) {
        return {outer.getName(), outer.getFieldId(), layout.allocTermField(outer.getFieldId()), false};
    }

    // used by create visitor
    void addTerm(Blueprint::UP term);

    void sort(InFlow in_flow) override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override;

    SearchIteratorUP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, fef::MatchData &global_md) const override;
    SearchIteratorUP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda) const override;
    SearchIteratorUP createFilterSearchImpl(FilterConstraint constraint) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(const ExecuteInfo &execInfo) override;
};

}
