// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include <vespa/searchlib/fef/matchdatalayout.h>

namespace search::queryeval {

class EquivBlueprint : public ComplexLeafBlueprint
{
private:
    HitEstimate                _estimate;
    fef::MatchDataLayout       _layout;
    std::vector<Blueprint::UP> _terms;
    std::vector<double>        _exactness;

public:
    EquivBlueprint(FieldSpecBaseList fields, fef::MatchDataLayout subtree_mdl);
    ~EquivBlueprint() override;

    // used by create visitor
    EquivBlueprint& addTerm(Blueprint::UP term, double exactness);

    void sort(InFlow in_flow) override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override;

    SearchIteratorUP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda) const override;
    SearchIteratorUP createFilterSearch(FilterConstraint constraint) const override;

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(const ExecuteInfo &execInfo) override;
    bool isEquiv() const noexcept final { return true; }
};

}
