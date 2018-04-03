// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchable.h"
#include <vespa/searchlib/fef/matchdatalayout.h>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

class DotProductBlueprint : public ComplexLeafBlueprint
{
    HitEstimate             _estimate;
    fef::MatchDataLayout    _layout;
    std::vector<int32_t>    _weights;
    std::vector<Blueprint*> _terms;

    DotProductBlueprint(const DotProductBlueprint &); // disabled
    DotProductBlueprint &operator=(const DotProductBlueprint &); // disabled

public:
    DotProductBlueprint(const FieldSpec &field);
    ~DotProductBlueprint();

    // used by create visitor
    FieldSpec getNextChildField(const FieldSpec &outer);

    // used by create visitor
    void addTerm(Blueprint::UP term, int32_t weight);

    SearchIteratorUP
    createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                     bool strict) const override;

    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(bool strict) override;
};

}
