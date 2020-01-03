// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchable.h"
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vector>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

class WeightedSetTermBlueprint : public ComplexLeafBlueprint
{
    HitEstimate             _estimate;
    fef::MatchDataLayout    _layout;
    FieldSpec               _children_field;
    std::vector<int32_t>    _weights;
    std::vector<Blueprint*> _terms;

    WeightedSetTermBlueprint(const WeightedSetTermBlueprint &); // disabled
    WeightedSetTermBlueprint &operator=(const WeightedSetTermBlueprint &); // disabled

public:
    WeightedSetTermBlueprint(const FieldSpec &field);
    ~WeightedSetTermBlueprint();

    // used by create visitor
    // matches signature in dot product blueprint for common blueprint
    // building code. Hands out the same field spec to all children.
    FieldSpec getNextChildField(const FieldSpec &) { return _children_field; }

    // used by create visitor
    void addTerm(Blueprint::UP term, int32_t weight);

    SearchIteratorUP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool strict) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;

private:
    void fetchPostings(bool strict) override;
};

}

