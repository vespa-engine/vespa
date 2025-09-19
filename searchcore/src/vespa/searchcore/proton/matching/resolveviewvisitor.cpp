// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resolveviewvisitor.h"
#include <vespa/document/datatype/positiondatatype.h>
#include <vespa/log/log.h>

LOG_SETUP(".proton.matching.resolveviewvisitor");

namespace proton::matching {

ResolveViewVisitor::ResolveViewVisitor(const matching::ViewResolver& resolver,
                                       const search::fef::IIndexEnvironment& indexEnv)
    : search::query::TemplateTermVisitor<ResolveViewVisitor, ProtonNodeTypes>(),
      _resolver(resolver),
      _indexEnv(indexEnv)
{
}

ResolveViewVisitor::~ResolveViewVisitor() = default;

void
ResolveViewVisitor::visit(ProtonLocationTerm& n) {
    // if injected by query.cpp, this should work:
    n.resolve(_resolver, _indexEnv);
    if (n.numFields() == 0) {
        // if received from QRS, this is needed:
        auto oldView = n.getView();
        auto newView = document::PositionDataType::getZCurveFieldName(oldView);
        n.setView(newView);
        n.resolve(_resolver, _indexEnv);
        LOG(debug, "ProtonLocationTerm found %zu field after view change %s -> %s",
            n.numFields(), oldView.c_str(), newView.c_str());
    }
}

void
ResolveViewVisitor::visit(ProtonNodeTypes::Equiv& n)
{
    visitChildren(n);
    n.resolveFromChildren(n.getChildren());
}

void ResolveViewVisitor::visit(ProtonNodeTypes::WordAlternatives& n) {
    for (const auto& tp : n.children) {
        visitTerm(*tp);
    }
    visitTerm(n);
}

void
ResolveViewVisitor::visit(ProtonNodeTypes::SameElement& n)
{
    visitChildren(n);
    visitTerm(n);
}

} // namespace
