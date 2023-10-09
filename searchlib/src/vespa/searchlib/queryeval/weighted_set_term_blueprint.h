// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "blueprint.h"
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vector>

namespace search::fef { class TermFieldMatchData; }

namespace search::queryeval {

class WeightedSetTermBlueprint : public ComplexLeafBlueprint
{
    fef::MatchDataLayout       _layout;
    FieldSpec                  _children_field;
    std::vector<int32_t>       _weights;
    std::vector<Blueprint::UP> _terms;

public:
    explicit WeightedSetTermBlueprint(const FieldSpec &field);
    WeightedSetTermBlueprint(const WeightedSetTermBlueprint &) = delete;
    WeightedSetTermBlueprint &operator=(const WeightedSetTermBlueprint &) = delete;
    ~WeightedSetTermBlueprint() override;

    // used by create visitor
    // matches signature in dot product blueprint for common blueprint
    // building code. Hands out the same field spec to all children.
    FieldSpecBase getNextChildField(FieldSpecBase) { return _children_field; }

    // used by create visitor
    void reserve(size_t num_children);
    void addTerm(Blueprint::UP term, int32_t weight, HitEstimate & estimate);
    void complete(HitEstimate estimate) {
        setEstimate(estimate);
    }

    SearchIteratorUP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda, bool strict) const override;
    SearchIteratorUP createFilterSearch(bool strict, FilterConstraint constraint) const override;
    std::unique_ptr<MatchingElementsSearch> create_matching_elements_search(const MatchingElementsFields &fields) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
private:
    void fetchPostings(const ExecuteInfo &execInfo) override;
};

}

