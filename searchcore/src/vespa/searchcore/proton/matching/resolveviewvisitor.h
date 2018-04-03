// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "querynodes.h"
#include "viewresolver.h"
#include <vespa/searchlib/query/tree/templatetermvisitor.h>
#include <vespa/searchlib/fef/iindexenvironment.h>

namespace proton::matching {

class ResolveViewVisitor
    : public search::query::TemplateTermVisitor<ResolveViewVisitor,
                                                ProtonNodeTypes>
{
    const ViewResolver &_resolver;
    const search::fef::IIndexEnvironment &_indexEnv;

public:
    ResolveViewVisitor(const matching::ViewResolver &resolver,
                       const search::fef::IIndexEnvironment &indexEnv)
        : _resolver(resolver), _indexEnv(indexEnv) {}

    template <class TermNode>
    void visitTerm(TermNode &n) { n.resolve(_resolver, _indexEnv); }

    virtual void visit(ProtonNodeTypes::Equiv &n) override {
        visitChildren(n);
        n.resolveFromChildren(n.getChildren());
    }
};

}

