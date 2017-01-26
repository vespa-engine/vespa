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

    virtual void visit(ProtonAnd &) {}
    virtual void visit(ProtonAndNot &) {}
    virtual void visit(ProtonNear &) {}
    virtual void visit(ProtonONear &) {}
    virtual void visit(ProtonOr &) {}
    virtual void visit(ProtonRank &) {}
    virtual void visit(ProtonWeakAnd &) {}

    virtual void visit(ProtonWeightedSetTerm &n) { visitTerm(n); }
    virtual void visit(ProtonDotProduct &n) { visitTerm(n); }
    virtual void visit(ProtonWandTerm &n) { visitTerm(n); }
    virtual void visit(ProtonPhrase &n) { visitTerm(n); }
    virtual void visit(ProtonEquiv &n) { visitTerm(n); }

    virtual void visit(ProtonNumberTerm &n) { visitTerm(n); }
    virtual void visit(ProtonLocationTerm &n) { visitTerm(n); }
    virtual void visit(ProtonPrefixTerm &n) { visitTerm(n); }
    virtual void visit(ProtonRangeTerm &n) { visitTerm(n); }
    virtual void visit(ProtonStringTerm &n) { visitTerm(n); }
    virtual void visit(ProtonSubstringTerm &n) { visitTerm(n); }
    virtual void visit(ProtonSuffixTerm &n) { visitTerm(n); }
    virtual void visit(ProtonPredicateQuery &) { }
    virtual void visit(ProtonRegExpTerm &n) { visitTerm(n); }
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
