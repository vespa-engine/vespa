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
    visitChildren(n);
    n.allocateTerms(_mdl);
}

void
MatchDataReserveVisitor::visit(ProtonNodeTypes::SameElement& n)
{
    visitChildren(n);
    n.allocateTerms(_mdl);
}

void
MatchDataReserveVisitor::visit(ProtonNodeTypes::WordAlternatives& n)
{
    n.allocateTerms(_mdl);
    for (const auto & child : n.children) {
        child->allocateTerms(_mdl);
    }
}

}
