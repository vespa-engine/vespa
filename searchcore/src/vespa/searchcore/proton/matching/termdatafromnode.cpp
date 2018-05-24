// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "termdatafromnode.h"
#include "querynodes.h"
#include <vespa/searchlib/query/tree/customtypevisitor.h>

namespace proton::matching {

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

    void visit(ProtonAnd &) override {}
    void visit(ProtonAndNot &) override {}
    void visit(ProtonNear &) override {}
    void visit(ProtonONear &) override {}
    void visit(ProtonOr &) override {}
    void visit(ProtonRank &) override {}
    void visit(ProtonWeakAnd &) override {}

    void visit(ProtonWeightedSetTerm &n) override { visitTerm(n); }
    void visit(ProtonDotProduct &n) override { visitTerm(n); }
    void visit(ProtonWandTerm &n) override { visitTerm(n); }
    void visit(ProtonPhrase &n) override { visitTerm(n); }
    void visit(ProtonSameElement &n) override { visitTerm(n); }
    void visit(ProtonEquiv &n) override { visitTerm(n); }

    void visit(ProtonNumberTerm &n) override { visitTerm(n); }
    void visit(ProtonLocationTerm &n) override { visitTerm(n); }
    void visit(ProtonPrefixTerm &n) override { visitTerm(n); }
    void visit(ProtonRangeTerm &n) override { visitTerm(n); }
    void visit(ProtonStringTerm &n) override { visitTerm(n); }
    void visit(ProtonSubstringTerm &n) override { visitTerm(n); }
    void visit(ProtonSuffixTerm &n) override { visitTerm(n); }
    void visit(ProtonPredicateQuery &) override { }
    void visit(ProtonRegExpTerm &n) override { visitTerm(n); }
};
}  // namespace

const ProtonTermData *
termDataFromNode(const search::query::Node &node)
{
    TermDataFromTermVisitor visitor;
    const_cast<search::query::Node &>(node).accept(visitor);
    return visitor.data;
}

}
