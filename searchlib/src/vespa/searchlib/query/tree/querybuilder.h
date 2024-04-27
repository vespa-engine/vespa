// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * The QueryBuilder builds a query tree. The exact type of the nodes
 * in the tree is defined by a traits class, which defines the actual
 * subclasses of the query nodes to use. Simple subclasses are defined
 * in "simplequery.h"
 *
 * To create a QueryBuilder that uses the simple query nodes, create
 * the builder like this:
 *
 * QueryBuilder<SimpleQueryNodeTypes> builder;
 *
 * Query trees are built using prefix traversal, e.g:
 *   builder.addOr(2);  // Two children
 *   builder.addStringTerm(term, view, id, weight);
 *   builder.addStringTerm(term, view, id, weight);
 *   Node::UP node = builder.build();
 */

#pragma once

#include "predicate_query_term.h"
#include "node.h"
#include "termnodes.h"
#include "const_bool_nodes.h"
#include <vespa/searchlib/query/weight.h>
#include <stack>

namespace search::query {

class Intermediate;
class Location;
class Range;
class TermVector;

class QueryBuilderBase
{
    class WeightOverride {
        bool _active;
        Weight _weight;
    public:
        WeightOverride() noexcept : _active(false), _weight(0) {}
        explicit WeightOverride(Weight weight) noexcept : _active(true), _weight(weight) {}
        void adjustWeight(Weight &weight) const { if (_active) weight = _weight; }
    };
    struct NodeInfo {
        Intermediate *node;
        int remaining_child_count;
        WeightOverride weight_override;
        NodeInfo(Intermediate *n, int c) noexcept : node(n), remaining_child_count(c) {}
    };
    Node::UP _root;
    std::stack<NodeInfo> _nodes;
    vespalib::string _error_msg;

protected:
    QueryBuilderBase();
    ~QueryBuilderBase();

    // Takes ownership of node.
    void addCompleteNode(Node *node);
    // Takes ownership of node.
    void addIntermediateNode(Intermediate *node, int child_count);
    // Activates a weight override for the current intermediate node.
    void setWeightOverride(const Weight &weight);
    // Resets weight if a weight override is active.
    void adjustWeight(Weight &weight) const {
        if (!_nodes.empty()) {
            _nodes.top().weight_override.adjustWeight(weight);
        }
    }

public:
    /**
     * Builds the query tree. Returns 0 if something went wrong.
     */
    Node::UP build();

    /**
     * Checks if an error has occurred.
     */
    bool hasError() const { return !_error_msg.empty(); }

    /**
     * If build failed, the reason is stored here.
     */
    vespalib::string error() const { return _error_msg; }

    /**
     * After an error, reset() must be called before attempting to
     * build a new query tree with the same builder.
     */
    void reset();

    void reportError(const vespalib::string &msg);
    void reportError(const vespalib::string &msg, const Node & incomming, const Node & root);
};


// These template functions create nodes based on a traits class.
// You may specialize these functions for your own traits class to have full
// control of the query node instantiation.

template <class NodeTypes>
typename NodeTypes::TrueQueryNode *create_true() { return new typename NodeTypes::TrueQueryNode; }

template <class NodeTypes>
typename NodeTypes::FalseQueryNode *create_false() { return new typename NodeTypes::FalseQueryNode; }

// Intermediate nodes
template <class NodeTypes>
typename NodeTypes::And *createAnd() { return new typename NodeTypes::And; }

template <class NodeTypes>
typename NodeTypes::AndNot *
createAndNot() { return new typename NodeTypes::AndNot; }

template <class NodeTypes>
typename NodeTypes::Or *createOr() { return new typename NodeTypes::Or; }

template <class NodeTypes>
typename NodeTypes::WeakAnd *createWeakAnd(uint32_t targetNumHits, vespalib::stringref view) {
    return new typename NodeTypes::WeakAnd(targetNumHits, view);
}
template <class NodeTypes>
typename NodeTypes::Equiv *createEquiv(int32_t id, Weight weight) {
    return new typename NodeTypes::Equiv(id, weight);
}
template <class NodeTypes>
typename NodeTypes::Phrase *createPhrase(vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::Phrase(view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::SameElement *createSameElement(vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::SameElement(view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::WeightedSetTerm *createWeightedSetTerm(uint32_t num_terms, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::WeightedSetTerm(num_terms, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::DotProduct *createDotProduct(uint32_t num_terms, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::DotProduct(num_terms, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::WandTerm *
createWandTerm(uint32_t num_terms, vespalib::stringref view, int32_t id, Weight weight, uint32_t targetNumHits, int64_t scoreThreshold, double thresholdBoostFactor) {
    return new typename NodeTypes::WandTerm(num_terms, view, id, weight, targetNumHits, scoreThreshold, thresholdBoostFactor);
}
template <class NodeTypes>
typename NodeTypes::Rank *createRank() {
     return new typename NodeTypes::Rank;
}

template <class NodeTypes>
typename NodeTypes::Near *createNear(size_t distance) {
    return new typename NodeTypes::Near(distance);
}
template <class NodeTypes>
typename NodeTypes::ONear *createONear(size_t distance) {
    return new typename NodeTypes::ONear(distance);
}

// Term nodes
template <class NodeTypes>
typename NodeTypes::NumberTerm *
createNumberTerm(vespalib::stringref term, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::NumberTerm(term, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::PrefixTerm *
createPrefixTerm(vespalib::stringref term, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::PrefixTerm(term, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::RangeTerm *
createRangeTerm(const Range &term, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::RangeTerm(term, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::StringTerm *
createStringTerm(vespalib::stringref term, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::StringTerm(term, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::SubstringTerm *
createSubstringTerm(vespalib::stringref term, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::SubstringTerm(term, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::SuffixTerm *
createSuffixTerm(vespalib::stringref term, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::SuffixTerm(term, view, id, weight);
}

template <class NodeTypes>
typename NodeTypes::LocationTerm *
createLocationTerm(const Location &loc, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::LocationTerm(loc, view, id, weight);
}

template <class NodeTypes>
typename NodeTypes::PredicateQuery *
createPredicateQuery(PredicateQueryTerm::UP term, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::PredicateQuery(std::move(term), view, id, weight);
}

template <class NodeTypes>
typename NodeTypes::RegExpTerm *
createRegExpTerm(vespalib::stringref term, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::RegExpTerm(term, view, id, weight);
}

template <class NodeTypes>
typename NodeTypes::NearestNeighborTerm *
create_nearest_neighbor_term(vespalib::stringref query_tensor_name, vespalib::stringref field_name,
                             int32_t id, Weight weight, uint32_t target_num_hits,
                             bool allow_approximate, uint32_t explore_additional_hits,
                             double distance_threshold)
{
    return new typename NodeTypes::NearestNeighborTerm(query_tensor_name, field_name, id, weight,
                                                       target_num_hits, allow_approximate, explore_additional_hits,
                                                       distance_threshold);
}

template <class NodeTypes>
typename NodeTypes::FuzzyTerm *
createFuzzyTerm(vespalib::stringref term, vespalib::stringref view, int32_t id, Weight weight,
                uint32_t max_edit_distance, uint32_t prefix_lock_length, bool prefix_match) {
    return new typename NodeTypes::FuzzyTerm(term, view, id, weight, max_edit_distance, prefix_lock_length, prefix_match);
}

template <class NodeTypes>
typename NodeTypes::InTerm *
create_in_term(std::unique_ptr<TermVector> terms, MultiTerm::Type type, vespalib::stringref view, int32_t id, Weight weight) {
    return new typename NodeTypes::InTerm(std::move(terms), type, view, id, weight);
}

template <class NodeTypes>
class QueryBuilder : public QueryBuilderBase {
    template <class T>
    T &addIntermediate(T *node, int child_count) {
        addIntermediateNode(node, child_count);
        return *node;
    }

    template <class T>
    T &addTerm(T *node) {
        addCompleteNode(node);
        return *node;
    }

public:
    using stringref = vespalib::stringref;
    typename NodeTypes::And &addAnd(int child_count) {
        return addIntermediate(createAnd<NodeTypes>(), child_count);
    }
    typename NodeTypes::AndNot &addAndNot(int child_count) {
        return addIntermediate(createAndNot<NodeTypes>(), child_count);
    }
    typename NodeTypes::Near &addNear(int child_count, size_t distance) {
        return addIntermediate(createNear<NodeTypes>(distance), child_count);
    }
    typename NodeTypes::ONear &addONear(int child_count, size_t distance) {
        return addIntermediate(createONear<NodeTypes>(distance), child_count);
    }
    typename NodeTypes::Or &addOr(int child_count) {
        return addIntermediate(createOr<NodeTypes>(), child_count);
    }
    typename NodeTypes::WeakAnd &addWeakAnd(int child_count, uint32_t targetNumHits, stringref view) {
        return addIntermediate(createWeakAnd<NodeTypes>(targetNumHits, view), child_count);
    }
    typename NodeTypes::Equiv &addEquiv(int child_count, int32_t id, Weight weight) {
        return addIntermediate(createEquiv<NodeTypes>(id, weight), child_count);
    }
    typename NodeTypes::Phrase &addPhrase(int child_count, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        typename NodeTypes::Phrase &node = addIntermediate(createPhrase<NodeTypes>(view, id, weight), child_count);
        setWeightOverride(weight);
        return node;
    }
    typename NodeTypes::SameElement &addSameElement(int child_count, stringref view, int32_t id, Weight weight) {
        return addIntermediate(createSameElement<NodeTypes>(view, id, weight), child_count);
    }
    typename NodeTypes::WeightedSetTerm &addWeightedSetTerm( int child_count, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        typename NodeTypes::WeightedSetTerm &node = addTerm(createWeightedSetTerm<NodeTypes>(child_count, view, id, weight));
        return node;
    }
    typename NodeTypes::DotProduct &addDotProduct( int child_count, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        typename NodeTypes::DotProduct &node = addTerm( createDotProduct<NodeTypes>(child_count, view, id, weight));
        return node;
    }
    typename NodeTypes::WandTerm &addWandTerm(
            int child_count, stringref view,
            int32_t id, Weight weight, uint32_t targetNumHits,
            int64_t scoreThreshold, double thresholdBoostFactor)
    {
        adjustWeight(weight);
        typename NodeTypes::WandTerm &node = addTerm(
                createWandTerm<NodeTypes>(child_count, view, id, weight, targetNumHits, scoreThreshold, thresholdBoostFactor));
        return node;
    }
    typename NodeTypes::Rank &addRank(int child_count) {
        return addIntermediate(createRank<NodeTypes>(), child_count);
    }

    typename NodeTypes::NumberTerm &addNumberTerm(stringref term, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createNumberTerm<NodeTypes>(term, view, id, weight));
    }
    typename NodeTypes::PrefixTerm &addPrefixTerm(stringref term, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createPrefixTerm<NodeTypes>(term, view, id, weight));
    }
    typename NodeTypes::RangeTerm &addRangeTerm(const Range &range, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createRangeTerm<NodeTypes>(range, view, id, weight));
    }
    typename NodeTypes::StringTerm &addStringTerm(stringref term, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createStringTerm<NodeTypes>(term, view, id, weight));
    }
    typename NodeTypes::SubstringTerm &addSubstringTerm(stringref t, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createSubstringTerm<NodeTypes>(t, view, id, weight));
    }
    typename NodeTypes::SuffixTerm &addSuffixTerm(stringref term, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createSuffixTerm<NodeTypes>(term, view, id, weight));
    }
    typename NodeTypes::LocationTerm &addLocationTerm(const Location &loc, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createLocationTerm<NodeTypes>(loc, view, id, weight));
    }
    typename NodeTypes::PredicateQuery &addPredicateQuery(PredicateQueryTerm::UP term, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createPredicateQuery<NodeTypes>(std::move(term), view, id, weight));
    }
    typename NodeTypes::RegExpTerm &addRegExpTerm(stringref term, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createRegExpTerm<NodeTypes>(term, view, id, weight));
    }
    typename NodeTypes::FuzzyTerm &addFuzzyTerm(stringref term, stringref view, int32_t id, Weight weight,
                                                uint32_t max_edit_distance, uint32_t prefix_lock_length,
                                                bool prefix_match) {
        adjustWeight(weight);
        return addTerm(createFuzzyTerm<NodeTypes>(term, view, id, weight, max_edit_distance, prefix_lock_length, prefix_match));
    }
    typename NodeTypes::NearestNeighborTerm &add_nearest_neighbor_term(stringref query_tensor_name, stringref field_name,
                                                                       int32_t id, Weight weight, uint32_t target_num_hits,
                                                                       bool allow_approximate, uint32_t explore_additional_hits,
                                                                       double distance_threshold)
    {
        adjustWeight(weight);
        return addTerm(create_nearest_neighbor_term<NodeTypes>(query_tensor_name, field_name, id, weight, target_num_hits, allow_approximate, explore_additional_hits, distance_threshold));
    }
    typename NodeTypes::TrueQueryNode &add_true_node() {
        return addTerm(create_true<NodeTypes>());
    }
    typename NodeTypes::FalseQueryNode &add_false_node() {
        return addTerm(create_false<NodeTypes>());
    }
    typename NodeTypes::InTerm& add_in_term(std::unique_ptr<TermVector> terms, MultiTerm::Type type, stringref view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(create_in_term<NodeTypes>(std::move(terms), type, view, id, weight));
    }
};

}
