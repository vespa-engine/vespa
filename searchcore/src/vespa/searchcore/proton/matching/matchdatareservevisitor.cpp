// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "matchdatareservevisitor.h"

#include <vespa/log/log.h>
LOG_SETUP(".proton.matching.matchdatareservevisitor");

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
    LOG(debug, "allocateTerms for WordAlternatives %zd fields", n.numFields());
    n.allocateTerms(_mdl);
    for (const auto & child : n.getChildren()) {
        auto* protonTerm = dynamic_cast<ProtonTermData*>(child.get());
        assert(protonTerm);
        protonTerm->allocateTerms(_mdl);
    }
}

void
MatchDataReserveVisitor::visit(ProtonNodeTypes::Phrase& n)
{
    LOG(debug, "allocateTerms for Phrase");
    n.allocateTerms(_mdl);
    visitChildren(n);
}

}
