// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include <vespa/searchlib/fef/matchdatalayout.h>

namespace search {
namespace queryeval {

class EquivBlueprint : public ComplexLeafBlueprint
{
private:
    FieldSpecBaseList          _fields;
    HitEstimate                _estimate;
    fef::MatchDataLayout       _layout;
    std::vector<Blueprint::UP> _terms;
    std::vector<double>        _exactness;

public:
    EquivBlueprint(const FieldSpecBaseList &fields, fef::MatchDataLayout subtree_mdl);
    virtual ~EquivBlueprint();

    // used by create visitor
    EquivBlueprint& addTerm(Blueprint::UP term, double exactness);

    SearchIteratorUP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool strict) const override;

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(bool strict) override;
    bool isEquiv() const override { return true; }
};

} // namespace queryeval
} // namespace search
