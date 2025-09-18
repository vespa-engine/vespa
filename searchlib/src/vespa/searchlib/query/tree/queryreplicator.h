// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "integer_term_vector.h"
#include "intermediatenodes.h"
#include "querybuilder.h"
#include "queryvisitor.h"
#include "string_term_vector.h"
#include "weighted_integer_term_vector.h"
#include "weighted_string_term_vector.h"
#include "termnodes.h"
#include <cassert>

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
    void copyState(const Term &original, Term &replica) {
        replica.setStateFrom(original);
    }

    void visitNodes(const std::vector<Node *> &nodes) {
        for (auto node : nodes) {
            node->accept(*this);
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
        _builder.addWeakAnd(node.getChildren().size(), node.getTargetNumHits(), node.getView());
        visitNodes(node.getChildren());
    }

    void visit(Equiv &node) override {
        _builder.addEquiv(node.getChildren().size(), node.getId(), node.getWeight());
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
        copyState(node, _builder.addPhrase(node.getChildren().size(), node.getView(),
                                           node.getId(), node.getWeight()).set_expensive(node.is_expensive()));
        visitNodes(node.getChildren());
    }

    void visit(SameElement &node) override {
        _builder.addSameElement(node.getChildren().size(), node.getView(),
                                node.getId(), node.getWeight()).set_expensive(node.is_expensive());
        visitNodes(node.getChildren());
    }

    void visit(WeightedSetTerm &node) override {
        auto & replica = _builder.addWeightedSetTerm(
                replicate_subterms(node), node.getType(), node.getView(), node.getId(), node.getWeight());
        copyState(node, replica);
    }

    void visit(DotProduct &node) override {
        auto & replica = _builder.addDotProduct(
                replicate_subterms(node), node.getType(), node.getView(), node.getId(), node.getWeight());
        copyState(node, replica);
    }

    void visit(WandTerm &node) override {
        auto & replica = _builder.addWandTerm(
                replicate_subterms(node), node.getType(),
                node.getView(), node.getId(), node.getWeight(),
                                              node.getTargetNumHits(),
                                              node.getScoreThreshold(),
                                              node.getThresholdBoostFactor());
        copyState(node, replica);
    }

    void visit(Rank &node) override {
        _builder.addRank(node.getChildren().size());
        visitNodes(node.getChildren());
    }

    void visit(NumberTerm &node) override {
        copyState(node, _builder.addNumberTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(LocationTerm &node) override {
        copyState(node,_builder.addLocationTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(PrefixTerm &node) override {
        copyState(node, _builder.addPrefixTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(RangeTerm &node) override {
        copyState(node, _builder.addRangeTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(StringTerm &node) override {
        copyState(node, _builder.addStringTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(SubstringTerm &node) override {
        copyState(node, _builder.addSubstringTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(SuffixTerm &node) override {
        copyState(node, _builder.addSuffixTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(PredicateQuery &node) override {
        copyState(node, _builder.addPredicateQuery(
                          std::make_unique<PredicateQueryTerm>(*node.getTerm()),
                          node.getView(), node.getId(), node.getWeight()));
    }

    void visit(RegExpTerm &node) override {
        copyState(node, _builder.addRegExpTerm(
                          node.getTerm(), node.getView(),
                          node.getId(), node.getWeight()));
    }

    void visit(NearestNeighborTerm &node) override {
        copyState(node, _builder.add_nearest_neighbor_term(node.get_query_tensor_name(), node.getView(),
                                                           node.getId(), node.getWeight(), node.get_target_num_hits(),
                                                           node.get_allow_approximate(), node.get_explore_additional_hits(),
                                                           node.get_distance_threshold()));
    }

    void visit(TrueQueryNode &) override {
        _builder.add_true_node();
    }

    void visit(FalseQueryNode &) override {
        _builder.add_false_node();
    }

    void visit(FuzzyTerm &node) override {
        copyState(node, _builder.addFuzzyTerm(
                node.getTerm(), node.getView(),
                node.getId(), node.getWeight(),
                node.max_edit_distance(), node.prefix_lock_length(),
                node.prefix_match()));
    }

    std::unique_ptr<TermVector> replicate_subterms(const MultiTerm& original) {
        uint32_t num_terms = original.getNumTerms();
        switch (original.getType()) {
        case MultiTerm::Type::STRING: {
            auto replica = std::make_unique<StringTermVector>(num_terms);
            for (uint32_t i = 0; i < num_terms; i++) {
                auto v = original.getAsString(i);
                replica->addTerm(v.first);
            }
            return replica;
        }
        case MultiTerm::Type::WEIGHTED_STRING: {
            auto replica = std::make_unique<WeightedStringTermVector>(num_terms);
            for (uint32_t i = 0; i < num_terms; i++) {
                auto v = original.getAsString(i);
                replica->addTerm(v.first, v.second);
            }
            return replica;
        }
        case MultiTerm::Type::INTEGER: {
            auto replica = std::make_unique<IntegerTermVector>(num_terms);
            for (uint32_t i = 0; i < original.getNumTerms(); i++) {
                auto v = original.getAsInteger(i);
                replica->addTerm(v.first);
            }
            return replica;
        }
        case MultiTerm::Type::WEIGHTED_INTEGER: {
            auto replica = std::make_unique<WeightedIntegerTermVector>(num_terms);
            for (uint32_t i = 0; i < original.getNumTerms(); i++) {
                auto v = original.getAsInteger(i);
                replica->addTerm(v.first, v.second);
            }
            return replica;
        }
        case MultiTerm::Type::UNKNOWN:
            assert(num_terms == 0);
        }
        return std::make_unique<WeightedStringTermVector>(num_terms);
    }
    void visit(InTerm& node) override {
        copyState(node, _builder.add_in_term(replicate_subterms(node), node.getType(), node.getView(), node.getId(), node.getWeight()));
    }
};

}
