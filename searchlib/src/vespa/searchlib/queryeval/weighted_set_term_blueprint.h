// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchable.h"
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <memory>
#include <vector>

namespace search {
namespace fef { class TermFieldMatchData; }

namespace queryeval {

class WeightedSetTermBlueprint : public ComplexLeafBlueprint
{
    HitEstimate             _estimate;
    std::vector<int32_t>    _weights;
    std::vector<Blueprint*> _terms;

    WeightedSetTermBlueprint(const WeightedSetTermBlueprint &); // disabled
    WeightedSetTermBlueprint &operator=(const WeightedSetTermBlueprint &); // disabled

public:
    WeightedSetTermBlueprint(const FieldSpec &field);
    ~WeightedSetTermBlueprint();

    // used by create visitor
    // matches signature in dot product blueprint for common blueprint
    // building code. Hands out its own field spec to children. NOTE:
    // this is only ok since children will never be unpacked.
    FieldSpec getNextChildField(const FieldSpec &outer) { return outer; }

    // used by create visitor
    void addTerm(Blueprint::UP term, int32_t weight);

    SearchIterator::UP createSearch(search::fef::MatchData &md, bool strict) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;

private:
    SearchIterator::UP createLeafSearch(const search::fef::TermFieldMatchDataArray &, bool) const override;
    void fetchPostings(bool strict) override;
};

}  // namespace search::queryeval
}  // namespace search

