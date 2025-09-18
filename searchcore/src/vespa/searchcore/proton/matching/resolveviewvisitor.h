// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "querynodes.h"
#include "viewresolver.h"
#include <vespa/searchlib/query/tree/templatetermvisitor.h>
#include <vespa/searchlib/fef/iindexenvironment.h>

namespace proton::matching {

class ResolveViewVisitor : public search::query::TemplateTermVisitor<ResolveViewVisitor, ProtonNodeTypes>
{
    const ViewResolver &_resolver;
    const search::fef::IIndexEnvironment &_indexEnv;

public:
    ResolveViewVisitor(const matching::ViewResolver& resolver, const search::fef::IIndexEnvironment& indexEnv);
    ~ResolveViewVisitor() override;

    template <class TermNode>
    void visitTerm(TermNode& n) { n.resolve(_resolver, _indexEnv); }

    void visit(ProtonLocationTerm& n) override;
    void visit(ProtonNodeTypes::Equiv& n) override;
    void visit(ProtonNodeTypes::SameElement &n) override;
};

}
