// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchable.h"
#include "irequestcontext.h"
#include <vespa/searchlib/fef/matchdatalayout.h>

namespace search {
namespace fef { class TermFieldMatchData; }

namespace queryeval {

class SimplePhraseBlueprint : public ComplexLeafBlueprint
{
private:
    const vespalib::Doom    _doom; 
    FieldSpec               _field;
    HitEstimate             _estimate;
    fef::MatchDataLayout    _layout;
    std::vector<Blueprint*> _terms;

    SimplePhraseBlueprint(const SimplePhraseBlueprint &); // disabled
    SimplePhraseBlueprint &operator=(const SimplePhraseBlueprint &); // disabled

public:
    SimplePhraseBlueprint(const FieldSpec &field, const IRequestContext & requestContext);
    ~SimplePhraseBlueprint();

    // used by create visitor
    FieldSpec getNextChildField(const FieldSpec &outer);

    // used by create visitor
    void addTerm(Blueprint::UP term);

    SearchIterator::UP
    createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                     bool strict) const override;
    void visitMembers(vespalib::ObjectVisitor &visitor) const override;
    void fetchPostings(bool strict) override;
};

}  // namespace search::queryeval
}  // namespace search
