// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termdatafromnode.h"
#include "querynodes.h"
#include <vespa/searchlib/query/tree/customtypevisitor.h>

namespace proton {
namespace matching {

namespace {
struct TermDataFromTermVisitor
    : public search::query::CustomTypeVisitor<ProtonNodeTypes>
{
    const ProtonTermData *data;
    TermDataFromTermVisitor() : data(0) {}

    template <class TermNode>
    void visitTerm(const TermNode &n) {
        data = &n;
    }

    virtual void visit(ProtonAnd &) override {}
    virtual void visit(ProtonAndNot &) override {}
    virtual void visit(ProtonNear &) override {}
    virtual void visit(ProtonONear &) override {}
    virtual void visit(ProtonOr &) override {}
    virtual void visit(ProtonRank &) override {}
    virtual void visit(ProtonWeakAnd &) override {}

    virtual void visit(ProtonWeightedSetTerm &n) override { visitTerm(n); }
    virtual void visit(ProtonDotProduct &n) override { visitTerm(n); }
    virtual void visit(ProtonWandTerm &n) override { visitTerm(n); }
    virtual void visit(ProtonPhrase &n) override { visitTerm(n); }
    virtual void visit(ProtonEquiv &n) override { visitTerm(n); }

    virtual void visit(ProtonNumberTerm &n) override { visitTerm(n); }
    virtual void visit(ProtonLocationTerm &n) override { visitTerm(n); }
    virtual void visit(ProtonPrefixTerm &n) override { visitTerm(n); }
    virtual void visit(ProtonRangeTerm &n) override { visitTerm(n); }
    virtual void visit(ProtonStringTerm &n) override { visitTerm(n); }
    virtual void visit(ProtonSubstringTerm &n) override { visitTerm(n); }
    virtual void visit(ProtonSuffixTerm &n) override { visitTerm(n); }
    virtual void visit(ProtonPredicateQuery &) override { }
    virtual void visit(ProtonRegExpTerm &n) override { visitTerm(n); }
};
}  // namespace

const ProtonTermData *
termDataFromNode(const search::query::Node &node)
{
    TermDataFromTermVisitor visitor;
    const_cast<search::query::Node &>(node).accept(visitor);
    return visitor.data;
}

}  // namespace matching
}  // namespace proton
