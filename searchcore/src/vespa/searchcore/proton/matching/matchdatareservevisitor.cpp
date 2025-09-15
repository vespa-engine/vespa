// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchdatareservevisitor.h"

namespace proton::matching {

MatchDataReserveVisitor::MatchDataReserveVisitor(search::fef::MatchDataLayout& mdl)
    : search::query::TemplateTermVisitor<MatchDataReserveVisitor, ProtonNodeTypes>(),
      _mdl(mdl)
{
}

MatchDataReserveVisitor::~MatchDataReserveVisitor() = default;

void
MatchDataReserveVisitor::visit(ProtonNodeTypes::Equiv& n)
{
    MatchDataReserveVisitor subAllocator(n.children_mdl);
    subAllocator.visitChildren(n);
    n.allocateTerms(_mdl);
}

void
MatchDataReserveVisitor::visit(ProtonNodeTypes::SameElement& n)
{
    MatchDataReserveVisitor subAllocator(n.subtree_mdl);
    subAllocator.visitChildren(n);
    n.allocateTerms(_mdl);
}

}
