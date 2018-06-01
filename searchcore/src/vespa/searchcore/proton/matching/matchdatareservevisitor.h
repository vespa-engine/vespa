// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "querynodes.h"
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/query/tree/templatetermvisitor.h>

namespace proton::matching {

/**
 * Visits all terms of a node tree, and allocates MatchData space for
 * each.
 */
class MatchDataReserveVisitor : public search::query::TemplateTermVisitor<MatchDataReserveVisitor, ProtonNodeTypes>
{
    search::fef::MatchDataLayout &_mdl;

public:
    template <class TermNode>
    void visitTerm(TermNode &n) { n.allocateTerms(_mdl); }

    void visit(ProtonNodeTypes::Equiv &n) override {
        MatchDataReserveVisitor subAllocator(n.children_mdl);
        for (size_t i = 0; i < n.getChildren().size(); ++i) {
            n.getChildren()[i]->accept(subAllocator);
        }
        n.allocateTerms(_mdl);
    }
    void visit(ProtonNodeTypes::SameElement &) override { }

    MatchDataReserveVisitor(search::fef::MatchDataLayout &mdl) : _mdl(mdl) {}
};

}

