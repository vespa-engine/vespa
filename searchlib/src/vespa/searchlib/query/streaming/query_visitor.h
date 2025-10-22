// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace search::streaming {

// Forward declarations
class AndQueryNode;
class AndNotQueryNode;
class DotProductTerm;
class EquivQueryNode;
class FalseNode;
class FuzzyTerm;
class InTerm;
class LocationTerm;
class NearQueryNode;
class NearestNeighborQueryNode;
class NumberTerm;
class ONearQueryNode;
class OrQueryNode;
class PhraseQueryNode;
class PredicateQuery;
class PrefixTerm;
class QueryTerm;
class RangeTerm;
class RankWithQueryNode;
class RegexpTerm;
class SameElementQueryNode;
class StringTerm;
class SubstringTerm;
class SuffixTerm;
class TrueNode;
class WandTerm;
class WeakAndQueryNode;
class WeightedSetTerm;
class WordAlternatives;

/**
 * Visitor interface for streaming query nodes.
 *
 * This provides a type-safe visitor pattern for streaming query nodes,
 * similar to search::query::QueryVisitor but for streaming-specific implementations.
 */
class QueryVisitor {
public:
    virtual ~QueryVisitor() = default;

    // Intermediate nodes
    virtual void visit(AndQueryNode &) = 0;
    virtual void visit(AndNotQueryNode &) = 0;
    virtual void visit(EquivQueryNode &) = 0;
    virtual void visit(NearQueryNode &) = 0;
    virtual void visit(ONearQueryNode &) = 0;
    virtual void visit(OrQueryNode &) = 0;
    virtual void visit(PhraseQueryNode &) = 0;
    virtual void visit(SameElementQueryNode &) = 0;
    virtual void visit(RankWithQueryNode &) = 0;
    virtual void visit(WeakAndQueryNode &) = 0;

    // Term nodes
    virtual void visit(FuzzyTerm &) = 0;
    virtual void visit(InTerm &) = 0;
    virtual void visit(LocationTerm &) = 0;
    virtual void visit(NearestNeighborQueryNode &) = 0;
    virtual void visit(NumberTerm &) = 0;
    virtual void visit(PredicateQuery &) = 0;
    virtual void visit(PrefixTerm &) = 0;
    virtual void visit(QueryTerm &) = 0;
    virtual void visit(RangeTerm &) = 0;
    virtual void visit(RegexpTerm &) = 0;
    virtual void visit(StringTerm &) = 0;
    virtual void visit(SubstringTerm &) = 0;
    virtual void visit(SuffixTerm &) = 0;

    // Multi-term nodes
    virtual void visit(DotProductTerm &) = 0;
    virtual void visit(WandTerm &) = 0;
    virtual void visit(WeightedSetTerm &) = 0;
    virtual void visit(WordAlternatives &) = 0;

    // Boolean nodes
    virtual void visit(TrueNode &) = 0;
    virtual void visit(FalseNode &) = 0;
};

/**
 * Base class for streaming visitors that provides default implementations
 * which call a generic handleNode() method.
 */
class QueryVisitorBase : public QueryVisitor {
protected:
    virtual void handleNode(AndQueryNode &node);
    virtual void handleNode(AndNotQueryNode &node);
    virtual void handleNode(EquivQueryNode &node);
    virtual void handleNode(NearQueryNode &node);
    virtual void handleNode(ONearQueryNode &node);
    virtual void handleNode(OrQueryNode &node);
    virtual void handleNode(PhraseQueryNode &node);
    virtual void handleNode(SameElementQueryNode &node);
    virtual void handleNode(RankWithQueryNode &node);
    virtual void handleNode(WeakAndQueryNode &node);
    virtual void handleNode(FuzzyTerm &node);
    virtual void handleNode(InTerm &node);
    virtual void handleNode(LocationTerm &node);
    virtual void handleNode(NearestNeighborQueryNode &node);
    virtual void handleNode(NumberTerm &node);
    virtual void handleNode(PredicateQuery &node);
    virtual void handleNode(PrefixTerm &node);
    virtual void handleNode(QueryTerm &node);
    virtual void handleNode(RangeTerm &node);
    virtual void handleNode(RegexpTerm &node);
    virtual void handleNode(StringTerm &node);
    virtual void handleNode(SubstringTerm &node);
    virtual void handleNode(SuffixTerm &node);
    virtual void handleNode(DotProductTerm &node);
    virtual void handleNode(WandTerm &node);
    virtual void handleNode(WeightedSetTerm &node);
    virtual void handleNode(WordAlternatives &node);
    virtual void handleNode(TrueNode &node);
    virtual void handleNode(FalseNode &node);

public:
    void visit(AndQueryNode &n) override { handleNode(n); }
    void visit(AndNotQueryNode &n) override { handleNode(n); }
    void visit(EquivQueryNode &n) override { handleNode(n); }
    void visit(NearQueryNode &n) override { handleNode(n); }
    void visit(ONearQueryNode &n) override { handleNode(n); }
    void visit(OrQueryNode &n) override { handleNode(n); }
    void visit(PhraseQueryNode &n) override { handleNode(n); }
    void visit(SameElementQueryNode &n) override { handleNode(n); }
    void visit(RankWithQueryNode &n) override { handleNode(n); }
    void visit(WeakAndQueryNode &n) override { handleNode(n); }
    void visit(FuzzyTerm &n) override { handleNode(n); }
    void visit(InTerm &n) override { handleNode(n); }
    void visit(LocationTerm &n) override { handleNode(n); }
    void visit(NearestNeighborQueryNode &n) override { handleNode(n); }
    void visit(NumberTerm &n) override { handleNode(n); }
    void visit(PredicateQuery &n) override { handleNode(n); }
    void visit(PrefixTerm &n) override { handleNode(n); }
    void visit(QueryTerm &n) override { handleNode(n); }
    void visit(RangeTerm &n) override { handleNode(n); }
    void visit(RegexpTerm &n) override { handleNode(n); }
    void visit(StringTerm &n) override { handleNode(n); }
    void visit(SubstringTerm &n) override { handleNode(n); }
    void visit(SuffixTerm &n) override { handleNode(n); }
    void visit(DotProductTerm &n) override { handleNode(n); }
    void visit(WandTerm &n) override { handleNode(n); }
    void visit(WeightedSetTerm &n) override { handleNode(n); }
    void visit(WordAlternatives &n) override { handleNode(n); }
    void visit(TrueNode &n) override { handleNode(n); }
    void visit(FalseNode &n) override { handleNode(n); }
};

}
