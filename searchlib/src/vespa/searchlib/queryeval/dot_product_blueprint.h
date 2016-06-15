// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchable.h"
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <memory>
#include <vector>

namespace search {
namespace fef { class TermFieldMatchData; }

namespace queryeval {

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
    virtual ~DotProductBlueprint();

    // used by create visitor
    FieldSpec getNextChildField(const FieldSpec &outer);

    // used by create visitor
    void addTerm(Blueprint::UP term, int32_t weight);

    virtual SearchIterator::UP
    createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                     bool strict) const;

    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;

    virtual void
    fetchPostings(bool strict);
};

}  // namespace search::queryeval
}  // namespace search

