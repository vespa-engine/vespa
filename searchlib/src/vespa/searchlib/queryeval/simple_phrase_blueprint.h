// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "searchable.h"
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>
#include <memory>
#include <vector>

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
    virtual ~SimplePhraseBlueprint();

    // used by create visitor
    FieldSpec getNextChildField(const FieldSpec &outer);

    // used by create visitor
    void addTerm(Blueprint::UP term);

    virtual SearchIterator::UP
    createLeafSearch(const search::fef::TermFieldMatchDataArray &tfmda,
                     bool strict) const;

    virtual void visitMembers(vespalib::ObjectVisitor &visitor) const;

    virtual void
    fetchPostings(bool strict);
};

}  // namespace search::queryeval
}  // namespace search
