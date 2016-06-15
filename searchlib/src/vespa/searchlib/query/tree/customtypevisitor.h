// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "queryvisitor.h"

namespace search {
namespace query {

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

    virtual void visit(And &n) { visit(static_cast<TAnd&>(n)); }
    virtual void visit(AndNot &n) { visit(static_cast<TAndNot&>(n)); }
    virtual void visit(Equiv &n) { visit(static_cast<TEquiv&>(n)); }
    virtual void visit(NumberTerm &n) { visit(static_cast<TNumberTerm&>(n)); }
    virtual void visit(LocationTerm &n) { visit(static_cast<TLocTrm&>(n)); }
    virtual void visit(Near &n) { visit(static_cast<TNear&>(n)); }
    virtual void visit(ONear &n) { visit(static_cast<TONear&>(n)); }
    virtual void visit(Or &n) { visit(static_cast<TOr&>(n)); }
    virtual void visit(Phrase &n) { visit(static_cast<TPhrase&>(n)); }
    virtual void visit(PrefixTerm &n) { visit(static_cast<TPrefixTerm&>(n)); }
    virtual void visit(RangeTerm &n) { visit(static_cast<TRangeTerm&>(n)); }
    virtual void visit(Rank &n) { visit(static_cast<TRank&>(n)); }
    virtual void visit(StringTerm &n) { visit(static_cast<TStringTerm&>(n)); }
    virtual void visit(SubstringTerm &n) { visit(static_cast<TSubstrTr&>(n)); }
    virtual void visit(SuffixTerm &n) { visit(static_cast<TSuffixTerm&>(n)); }
    virtual void visit(WeakAnd &n) { visit(static_cast<TWeakAnd&>(n)); }
    virtual void visit(WeightedSetTerm &n)
    { visit(static_cast<TWeightedSetTerm&>(n)); }
    virtual void visit(DotProduct &n) { visit(static_cast<TDotProduct&>(n)); }
    virtual void visit(WandTerm &n) { visit(static_cast<TWandTerm&>(n)); }
    virtual void visit(PredicateQuery &n)
    { visit(static_cast<TPredicateQuery&>(n)); }
    virtual void visit(RegExpTerm &n) { visit(static_cast<TRegExpTerm&>(n)); }
};

}  // namespace query
}  // namespace search

