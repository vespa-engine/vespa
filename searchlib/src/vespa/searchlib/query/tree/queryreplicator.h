// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "intermediatenodes.h"
#include "querybuilder.h"
#include "queryvisitor.h"
#include "termnodes.h"

namespace search::query {

/**
 * Creates a new query tree based on an existing one. The traits class
 * specifies what concrete types the query tree classes should have.
 */
template <class NodeTypes>
class QueryReplicator : private QueryVisitor {
    QueryBuilder<NodeTypes> _builder;

public:
    Node::UP replicate(const Node &node) {
        // The visitor doesn't deal with const nodes. However, we are
        // not changing the node, so we can safely remove the const.
        const_cast<Node &>(node).accept(*this);
        return _builder.build();
    }

private:
    void visitNodes(const std::vector<Node *> &nodes) {
        for (size_t i = 0; i < nodes.size(); ++i) {
            nodes[i]->accept(*this);
        }
    }

    void visit(And &node) override {
        _builder.addAnd(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void visit(AndNot &node) override {
        _builder.addAndNot(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void visit(WeakAnd &node) override {
        _builder.addWeakAnd(node.getChildren().size(), node.getMinHits(), node.getView());
        visitNodes(node.getChildren());
    }

    void visit(Equiv &node) override {
        _builder.addEquiv(node.getChildren().size(), node.getId(), node.getWeight())
            .setTermIndex(node.getTermIndex());
        visitNodes(node.getChildren());
    }

    void visit(Near &node) override {
        _builder.addNear(node.getChildren().size(), node.getDistance());
        visitNodes(node.getChildren());
    }

    void visit(ONear &node) override {
        _builder.addONear(node.getChildren().size(), node.getDistance());
        visitNodes(node.getChildren());
    }

    void visit(Or &node) override {
        _builder.addOr(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void visit(Phrase &node) override {
        replicate(node, _builder.addPhrase(node.getChildren().size(), node.getView(),
                                           node.getId(), node.getWeight()));
        visitNodes(node.getChildren());
    }

    void visit(SameElement &node) override {
        _builder.addSameElement(node.getChildren().size(), node.getView());
        visitNodes(node.getChildren());
    }

    void visit(WeightedSetTerm &node) override {
        replicate(node, _builder.addWeightedSetTerm(node.getChildren().size(), node.getView(),
                                                    node.getId(), node.getWeight()));
        visitNodes(node.getChildren());
    }

    void visit(DotProduct &node) override {
        replicate(node, _builder.addDotProduct(node.getChildren().size(), node.getView(),
                                               node.getId(), node.getWeight()));
        visitNodes(node.getChildren());
    }

    void visit(WandTerm &node) override {
        replicate(node, _builder.addWandTerm(node.getChildren().size(),
                                             node.getView(),
                                             node.getId(), node.getWeight(),
                                             node.getTargetNumHits(),
                                             node.getScoreThreshold(),
                                             node.getThresholdBoostFactor()));
        visitNodes(node.getChildren());
    }

    void visit(Rank &node) override {
        _builder.addRank(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void replicate(const Term &original, Term &replica) {
        replica.setTermIndex(original.getTermIndex());
        replica.setRanked(original.isRanked());
    }

    void visit(NumberTerm &node) override {
        replicate(node, _builder.addNumberTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(LocationTerm &node) override {
        replicate(node,_builder.addLocationTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(PrefixTerm &node) override {
        replicate(node, _builder.addPrefixTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(RangeTerm &node) override {
        replicate(node, _builder.addRangeTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(StringTerm &node) override {
        replicate(node, _builder.addStringTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(SubstringTerm &node) override {
        replicate(node, _builder.addSubstringTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(SuffixTerm &node) override {
        replicate(node, _builder.addSuffixTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(PredicateQuery &node) override {
        replicate(node, _builder.addPredicateQuery(
                          PredicateQueryTerm::UP(new PredicateQueryTerm(*node.getTerm())),
                          node.getView(), node.getId(), node.getWeight()));
    }

    void visit(RegExpTerm &node) override {
        replicate(node, _builder.addRegExpTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }
};

}
