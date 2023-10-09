// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include <vespa/searchlib/fef/matchdatalayout.h>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

class DotProductBlueprint : public ComplexLeafBlueprint
{
    fef::MatchDataLayout       _layout;
    std::vector<int32_t>       _weights;
    std::vector<Blueprint::UP> _terms;

public:
    explicit DotProductBlueprint(const FieldSpec &field);
    DotProductBlueprint(const DotProductBlueprint &) = delete;
    DotProductBlueprint &operator=(const DotProductBlueprint &) = delete;
    ~DotProductBlueprint() override;

    // used by create visitor
    FieldSpecBase getNextChildField(FieldSpecBase parent) {
        return {parent.getFieldId(), _layout.allocTermField(parent.getFieldId()), false};
    }

    // used by create visitor
    void reserve(size_t num_children);
    void addTerm(Blueprint::UP term, int32_t weight, HitEstimate & estimate);
    void complete(HitEstimate estimate) {
        setEstimate(estimate);
    }

    SearchIteratorUP createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda, bool strict) const override;
    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override;

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(const ExecuteInfo &execInfo) override;
};

}
