// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "intermediatenodes.h"
#include "querybuilder.h"
#include "queryvisitor.h"
#include "termnodes.h"

namespace search {
namespace query {

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

    virtual void visit(And &node) {
        _builder.addAnd(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    virtual void visit(AndNot &node) {
        _builder.addAndNot(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    virtual void visit(WeakAnd &node) {
        _builder.addWeakAnd(node.getChildren().size(), node.getMinHits(), node.getView());
        visitNodes(node.getChildren());
    }

    virtual void visit(Equiv &node) {
        _builder.addEquiv(node.getChildren().size(), node.getId(), node.getWeight())
            .setTermIndex(node.getTermIndex());
        visitNodes(node.getChildren());
    }

    virtual void visit(Near &node) {
        _builder.addNear(node.getChildren().size(), node.getDistance());
        visitNodes(node.getChildren());
    }

    virtual void visit(ONear &node) {
        _builder.addONear(node.getChildren().size(), node.getDistance());
        visitNodes(node.getChildren());
    }

    virtual void visit(Or &node) {
        _builder.addOr(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    virtual void visit(Phrase &node) {
        replicate(node, _builder.addPhrase(node.getChildren().size(),
                                           node.getView(),
                                           node.getId(), node.getWeight()));
        visitNodes(node.getChildren());
    }

    virtual void visit(WeightedSetTerm &node) {
        replicate(node, _builder.addWeightedSetTerm(node.getChildren().size(),
                                                    node.getView(),
                                                    node.getId(), node.getWeight()));
        visitNodes(node.getChildren());
    }

    virtual void visit(DotProduct &node) {
        replicate(node, _builder.addDotProduct(node.getChildren().size(),
                                               node.getView(),
                                               node.getId(), node.getWeight()));
        visitNodes(node.getChildren());
    }

    virtual void visit(WandTerm &node) {
        replicate(node, _builder.addWandTerm(node.getChildren().size(),
                                             node.getView(),
                                             node.getId(), node.getWeight(),
                                             node.getTargetNumHits(),
                                             node.getScoreThreshold(),
                                             node.getThresholdBoostFactor()));
        visitNodes(node.getChildren());
    }

    virtual void visit(Rank &node) {
        _builder.addRank(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void replicate(const Term &original, Term &replica) {
        replica.setTermIndex(original.getTermIndex());
        replica.setRanked(original.isRanked());
    }

    virtual void visit(NumberTerm &node) {
        replicate(node, _builder.addNumberTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    virtual void visit(LocationTerm &node) {
        replicate(node,_builder.addLocationTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    virtual void visit(PrefixTerm &node) {
        replicate(node, _builder.addPrefixTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    virtual void visit(RangeTerm &node) {
        replicate(node, _builder.addRangeTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    virtual void visit(StringTerm &node) {
        replicate(node, _builder.addStringTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    virtual void visit(SubstringTerm &node) {
        replicate(node, _builder.addSubstringTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    virtual void visit(SuffixTerm &node) {
        replicate(node, _builder.addSuffixTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    virtual void visit(PredicateQuery &node) {
        replicate(node, _builder.addPredicateQuery(
                          PredicateQueryTerm::UP(new PredicateQueryTerm(
                                          *node.getTerm())),
                          node.getView(), node.getId(), node.getWeight()));
    }

    virtual void visit(RegExpTerm &node) {
        replicate(node, _builder.addRegExpTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }
};

}  // namespace query
}  // namespace search

