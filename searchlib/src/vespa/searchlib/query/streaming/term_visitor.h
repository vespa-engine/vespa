// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "query_visitor.h"

namespace search::streaming {

/**
 * Base visitor class that automatically traverses connector nodes.
 * Subclasses must implement visit() methods for all term types they want to handle.
 *
 * This visitor provides automatic traversal of connector nodes (AND, OR, NEAR, etc.)
 * while requiring explicit implementations for all term nodes, providing type safety
 * without template complexity.
 *
 * Usage:
 *   class MyTermVisitor : public TermVisitor {
 *   protected:
 *       void visit(StringTerm &term) override {
 *           // Handle StringTerm
 *       }
 *       void visit(PrefixTerm &term) override {
 *           // Handle PrefixTerm
 *       }
 *       // Implement all other term types as needed
 *   };
 */
class TermVisitor : public QueryVisitor {
protected:
    /**
     * Helper method to traverse children of connector nodes.
     * Can be overridden if custom traversal logic is needed.
     */
    virtual void visitChildren(QueryConnector &node) {
        for (auto & child : node.getChildren()) {
            child->accept(*this);
        }
    }

    // Connector nodes - auto-traverse by default
    void visit(AndQueryNode &n) override { visitChildren(n); }
    void visit(AndNotQueryNode &n) override { visitChildren(n); }
    void visit(EquivQueryNode &n) override { visitChildren(n); }
    void visit(NearQueryNode &n) override { visitChildren(n); }
    void visit(ONearQueryNode &n) override { visitChildren(n); }
    void visit(OrQueryNode &n) override { visitChildren(n); }
    void visit(PhraseQueryNode &n) override { visitChildren(n); }
    void visit(SameElementQueryNode &n) override { visitChildren(n); }
    void visit(RankWithQueryNode &n) override { visitChildren(n); }
    void visit(WeakAndQueryNode &n) override { visitChildren(n); }

    // Boolean nodes - default no-op, can be overridden
    void visit(TrueNode &n) override { (void)n; }
    void visit(FalseNode &n) override { (void)n; }

    // Term nodes - subclasses must implement these
    void visit(FuzzyTerm &n) override = 0;
    void visit(InTerm &n) override = 0;
    void visit(LocationTerm &n) override = 0;
    void visit(NearestNeighborQueryNode &n) override = 0;
    void visit(NumberTerm &n) override = 0;
    void visit(PredicateQuery &n) override = 0;
    void visit(PrefixTerm &n) override = 0;
    void visit(QueryTerm &n) override = 0;
    void visit(RangeTerm &n) override = 0;
    void visit(RegexpTerm &n) override = 0;
    void visit(StringTerm &n) override = 0;
    void visit(SubstringTerm &n) override = 0;
    void visit(SuffixTerm &n) override = 0;

    // Multi-term nodes - subclasses must implement these
    void visit(DotProductTerm &n) override = 0;
    void visit(WandTerm &n) override = 0;
    void visit(WeightedSetTerm &n) override = 0;
    void visit(WordAlternatives &n) override = 0;
};

}
