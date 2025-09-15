// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "querynodes.h"
#include <vespa/searchlib/query/tree/templatetermvisitor.h>

namespace proton::matching {

/**
 * Visits all terms of a node tree, and allocates MatchData space for
 * each.
 */
class MatchDataReserveVisitor : public search::query::TemplateTermVisitor<MatchDataReserveVisitor, ProtonNodeTypes>
{
    search::fef::MatchDataLayout& _mdl;

public:
    MatchDataReserveVisitor(search::fef::MatchDataLayout& mdl);
    ~MatchDataReserveVisitor() override;

    template <class TermNode>
    void visitTerm(TermNode& n) { n.allocateTerms(_mdl); }

    void visit(ProtonNodeTypes::Equiv& n) override;
};

}
