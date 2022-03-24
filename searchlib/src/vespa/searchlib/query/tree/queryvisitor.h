// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace search::query {

class And;
class AndNot;
class Equiv;
class NumberTerm;
class LocationTerm;
class Near;
class ONear;
class Or;
class Phrase;
class PrefixTerm;
class RangeTerm;
class Rank;
class StringTerm;
class SubstringTerm;
class SuffixTerm;
class WeakAnd;
class WeightedSetTerm;
class DotProduct;
class WandTerm;
class PredicateQuery;
class RegExpTerm;
class SameElement;
class NearestNeighborTerm;
class TrueQueryNode;
class FalseQueryNode;
class FuzzyTerm;

struct QueryVisitor {
    virtual ~QueryVisitor() {}

    virtual void visit(And &) = 0;
    virtual void visit(AndNot &) = 0;
    virtual void visit(Equiv &) = 0;
    virtual void visit(NumberTerm &) = 0;
    virtual void visit(LocationTerm &) = 0;
    virtual void visit(Near &) = 0;
    virtual void visit(ONear &) = 0;
    virtual void visit(Or &) = 0;
    virtual void visit(Phrase &) = 0;
    virtual void visit(SameElement &node) = 0;
    virtual void visit(PrefixTerm &) = 0;
    virtual void visit(RangeTerm &) = 0;
    virtual void visit(Rank &) = 0;
    virtual void visit(StringTerm &) = 0;
    virtual void visit(SubstringTerm &) = 0;
    virtual void visit(SuffixTerm &) = 0;
    virtual void visit(WeakAnd &) = 0;
    virtual void visit(WeightedSetTerm &) = 0;
    virtual void visit(DotProduct &) = 0;
    virtual void visit(WandTerm &) = 0;
    virtual void visit(PredicateQuery &) = 0;
    virtual void visit(RegExpTerm &) = 0;
    virtual void visit(NearestNeighborTerm &) = 0;
    virtual void visit(TrueQueryNode &) = 0;
    virtual void visit(FalseQueryNode &) = 0;
    virtual void visit(FuzzyTerm &) = 0;
};

}

