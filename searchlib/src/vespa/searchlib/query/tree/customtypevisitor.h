// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryvisitor.h"

namespace search::query {

/**
 * By typedefing a (complete) set of subclasses to the query nodes in
 * a traits class, you can get the CustomTypeVisitor to visit those
 * types instead of their base classes.
 *
 * The traits class must define the following types:
 * And, AndNot, Equiv, NumberTerm, Near, ONear, Or,
 * Phrase, PrefixTerm, RangeTerm, Rank, StringTerm, SubstringTerm,
 * SuffixTerm, WeakAnd, WeightedSetTerm, DotProduct, RegExpTerm
 *
 * See customtypevisitor_test.cpp for an example.
 *
 * Please note that your CustomTypeVisitor<T> subclass should NOT
 * implement any of the regular QueryVisitor member functions, as this
 * would interfere with the routing.
 */
template <class NodeTypes>
class CustomTypeVisitor : public QueryVisitor {
public:
    virtual ~CustomTypeVisitor() {}

    virtual void visit(typename NodeTypes::And &) = 0;
    virtual void visit(typename NodeTypes::AndNot &) = 0;
    virtual void visit(typename NodeTypes::Equiv &) = 0;
    virtual void visit(typename NodeTypes::NumberTerm &) = 0;
    virtual void visit(typename NodeTypes::LocationTerm &) = 0;
    virtual void visit(typename NodeTypes::Near &) = 0;
    virtual void visit(typename NodeTypes::ONear &) = 0;
    virtual void visit(typename NodeTypes::Or &) = 0;
    virtual void visit(typename NodeTypes::Phrase &) = 0;
    virtual void visit(typename NodeTypes::SameElement &) = 0;
    virtual void visit(typename NodeTypes::PrefixTerm &) = 0;
    virtual void visit(typename NodeTypes::RangeTerm &) = 0;
    virtual void visit(typename NodeTypes::Rank &) = 0;
    virtual void visit(typename NodeTypes::StringTerm &) = 0;
    virtual void visit(typename NodeTypes::SubstringTerm &) = 0;
    virtual void visit(typename NodeTypes::SuffixTerm &) = 0;
    virtual void visit(typename NodeTypes::WeakAnd &) = 0;
    virtual void visit(typename NodeTypes::WeightedSetTerm &) = 0;
    virtual void visit(typename NodeTypes::DotProduct &) = 0;
    virtual void visit(typename NodeTypes::WandTerm &) = 0;
    virtual void visit(typename NodeTypes::PredicateQuery &) = 0;
    virtual void visit(typename NodeTypes::RegExpTerm &) = 0;

private:
    // Route QueryVisit requests to the correct custom type.

    typedef typename NodeTypes::And TAnd;
    typedef typename NodeTypes::AndNot TAndNot;
    typedef typename NodeTypes::Equiv TEquiv;
    typedef typename NodeTypes::NumberTerm TNumberTerm;
    typedef typename NodeTypes::LocationTerm TLocTrm;
    typedef typename NodeTypes::Near TNear;
    typedef typename NodeTypes::ONear TONear;
    typedef typename NodeTypes::Or TOr;
    typedef typename NodeTypes::Phrase TPhrase;
    typedef typename NodeTypes::SameElement TSameElement;
    typedef typename NodeTypes::PrefixTerm TPrefixTerm;
    typedef typename NodeTypes::RangeTerm TRangeTerm;
    typedef typename NodeTypes::Rank TRank;
    typedef typename NodeTypes::StringTerm TStringTerm;
    typedef typename NodeTypes::SubstringTerm TSubstrTr;
    typedef typename NodeTypes::SuffixTerm TSuffixTerm;
    typedef typename NodeTypes::WeakAnd TWeakAnd;
    typedef typename NodeTypes::WeightedSetTerm TWeightedSetTerm;
    typedef typename NodeTypes::DotProduct TDotProduct;
    typedef typename NodeTypes::WandTerm TWandTerm;
    typedef typename NodeTypes::PredicateQuery TPredicateQuery;
    typedef typename NodeTypes::RegExpTerm TRegExpTerm;

    void visit(And &n) override { visit(static_cast<TAnd&>(n)); }
    void visit(AndNot &n) override { visit(static_cast<TAndNot&>(n)); }
    void visit(Equiv &n) override { visit(static_cast<TEquiv&>(n)); }
    void visit(NumberTerm &n) override { visit(static_cast<TNumberTerm&>(n)); }
    void visit(LocationTerm &n) override { visit(static_cast<TLocTrm&>(n)); }
    void visit(Near &n) override { visit(static_cast<TNear&>(n)); }
    void visit(ONear &n) override { visit(static_cast<TONear&>(n)); }
    void visit(Or &n) override { visit(static_cast<TOr&>(n)); }
    void visit(Phrase &n) override { visit(static_cast<TPhrase&>(n)); }
    void visit(SameElement &n) override { visit(static_cast<TSameElement &>(n)); }
    void visit(PrefixTerm &n) override { visit(static_cast<TPrefixTerm&>(n)); }
    void visit(RangeTerm &n) override { visit(static_cast<TRangeTerm&>(n)); }
    void visit(Rank &n) override { visit(static_cast<TRank&>(n)); }
    void visit(StringTerm &n) override { visit(static_cast<TStringTerm&>(n)); }
    void visit(SubstringTerm &n) override { visit(static_cast<TSubstrTr&>(n)); }
    void visit(SuffixTerm &n) override { visit(static_cast<TSuffixTerm&>(n)); }
    void visit(WeakAnd &n) override { visit(static_cast<TWeakAnd&>(n)); }
    void visit(WeightedSetTerm &n) override { visit(static_cast<TWeightedSetTerm&>(n)); }
    void visit(DotProduct &n) override { visit(static_cast<TDotProduct&>(n)); }
    void visit(WandTerm &n) override { visit(static_cast<TWandTerm&>(n)); }
    void visit(PredicateQuery &n) override { visit(static_cast<TPredicateQuery&>(n)); }
    void visit(RegExpTerm &n) override { visit(static_cast<TRegExpTerm&>(n)); }
};

}
