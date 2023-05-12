// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

    SearchIteratorUP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool strict) const override;
    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override;

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(const ExecuteInfo &execInfo) override;
    bool isEquiv() const override { return true; }
};

}
