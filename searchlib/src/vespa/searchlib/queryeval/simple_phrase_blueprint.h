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
    fef::MatchDataLayout       _layout;
    std::vector<Blueprint::UP> _terms;

public:
    SimplePhraseBlueprint(const FieldSpec &field, bool expensive);
    SimplePhraseBlueprint(const SimplePhraseBlueprint &) = delete;
    SimplePhraseBlueprint &operator=(const SimplePhraseBlueprint &) = delete;

    ~SimplePhraseBlueprint() override;

    // used by create visitor
    FieldSpec getNextChildField(const FieldSpec &outer);

    // used by create visitor
    void addTerm(Blueprint::UP term);

    void sort(InFlow in_flow, const Options &opts) override;
    FlowStats calculate_flow_stats(uint32_t docid_limit) const override;
    
    SearchIteratorUP createLeafSearch(const fef::TermFieldMatchDataArray &tfmda) const override;
    SearchIteratorUP createFilterSearch(FilterConstraint constraint) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(const ExecuteInfo &execInfo) override;
};

}
