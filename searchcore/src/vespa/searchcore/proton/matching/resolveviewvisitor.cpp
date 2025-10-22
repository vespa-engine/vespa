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
    LOG(debug, "resolve WordAlternatives");
    visitTerm(n);
    ViewResolver fixedResolver;
    LOG(debug, "ResolveViewVisitor visit WordAlternatives with %zd fields, use fixedResolver %p",
        n.numFields(), &fixedResolver);
    for (size_t i = 0; i < n.numFields(); i++) {
        fixedResolver.add("", n.field(i).getName());
    }
    ResolveViewVisitor fixedVisitor(fixedResolver, _indexEnv);
    for (const auto& child : n.getChildren()) {
        auto* protonTerm = dynamic_cast<ProtonStringTerm *>(child.get());
        if (protonTerm) {
            protonTerm->accept(fixedVisitor);
        } else {
            LOG(warning, "child of WordAlternatives is not a ProtonStringTerm");
        }
    }
}

void
ResolveViewVisitor::visit(ProtonNodeTypes::SameElement& n)
{
    visitChildren(n);
    visitTerm(n);
}


void ResolveViewVisitor::visit(ProtonNodeTypes::Phrase& n) {
    visitTerm(n);
    ViewResolver fixedResolver;
    LOG(debug, "ResolveViewVisitor visit Phrase with %zd fields, use fixedResolver %p",
        n.numFields(), &fixedResolver);
    for (size_t i = 0; i < n.numFields(); i++) {
        fixedResolver.add("", n.field(i).getName());
    }
    ResolveViewVisitor fixedVisitor(fixedResolver, _indexEnv);
    fixedVisitor.visitChildren(n);
}

} // namespace
