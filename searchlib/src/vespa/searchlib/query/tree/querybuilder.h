// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
#include <stack>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/searchlib/query/weight.h>
#include "node.h"

namespace search::query {

class Intermediate;
class Location;
class Range;

class QueryBuilderBase
{
    class WeightOverride {
        bool _active;
        Weight _weight;
    public:
        WeightOverride() : _active(false), _weight(0) {}
        WeightOverride(Weight weight) : _active(true), _weight(weight) {}
        void adjustWeight(Weight &weight) const { if (_active) weight = _weight; }
    };
    struct NodeInfo {
        Intermediate *node;
        int remaining_child_count;
        WeightOverride weight_override;
        NodeInfo(Intermediate *n, int c) : node(n), remaining_child_count(c) {}
    };
    Node::UP _root;
    std::stack<NodeInfo> _nodes;
    vespalib::string _error_msg;

    void reportError(const vespalib::string &msg);
    void reportError(const vespalib::string &msg, const Node & incomming, const Node & root);

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
    vespalib::string error() { return _error_msg; }

    /**
     * After an error, reset() must be called before attempting to
     * build a new query tree with the same builder.
     */
    void reset();
};


// These template functions create nodes based on a traits class.
// You may specialize these functions for your own traits class to have full
// control of the query node instantiation.

// Intermediate nodes
template <class NodeTypes>
typename NodeTypes::And *createAnd() { return new typename NodeTypes::And; }

template <class NodeTypes>
typename NodeTypes::AndNot *
createAndNot() { return new typename NodeTypes::AndNot; }

template <class NodeTypes>
typename NodeTypes::Or *createOr() { return new typename NodeTypes::Or; }

template <class NodeTypes>
typename NodeTypes::WeakAnd *createWeakAnd(uint32_t minHits, const vespalib::stringref & view) {
    return new typename NodeTypes::WeakAnd(minHits, view);
}
template <class NodeTypes>
typename NodeTypes::Equiv *createEquiv(int32_t id, Weight weight) {
    return new typename NodeTypes::Equiv(id, weight);
}
template <class NodeTypes>
typename NodeTypes::Phrase *createPhrase(const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::Phrase(view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::SameElement *createSameElement(const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::SameElement(view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::WeightedSetTerm *createWeightedSetTerm(const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::WeightedSetTerm(view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::DotProduct *createDotProduct(const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::DotProduct(view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::WandTerm *
createWandTerm(const vespalib::stringref &view, int32_t id, Weight weight, uint32_t targetNumHits, int64_t scoreThreshold, double thresholdBoostFactor) {
    return new typename NodeTypes::WandTerm(view, id, weight, targetNumHits, scoreThreshold, thresholdBoostFactor);
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
createNumberTerm(const vespalib::stringref &term, const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::NumberTerm(term, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::PrefixTerm *
createPrefixTerm(const vespalib::stringref &term, const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::PrefixTerm(term, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::RangeTerm *
createRangeTerm(const Range &term, const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::RangeTerm(term, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::StringTerm *
createStringTerm(const vespalib::stringref &term, const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::StringTerm(term, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::SubstringTerm *
createSubstringTerm(const vespalib::stringref &term, const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::SubstringTerm(term, view, id, weight);
}
template <class NodeTypes>
typename NodeTypes::SuffixTerm *
createSuffixTerm(const vespalib::stringref &term, const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::SuffixTerm(term, view, id, weight);
}

template <class NodeTypes>
typename NodeTypes::LocationTerm *
createLocationTerm(const Location &loc, const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::LocationTerm(loc, view, id, weight);
}

template <class NodeTypes>
typename NodeTypes::PredicateQuery *
createPredicateQuery(PredicateQueryTerm::UP term, const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::PredicateQuery(std::move(term), view, id, weight);
}

template <class NodeTypes>
typename NodeTypes::RegExpTerm *
createRegExpTerm(const vespalib::stringref &term, const vespalib::stringref &view, int32_t id, Weight weight) {
    return new typename NodeTypes::RegExpTerm(term, view, id, weight);
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
    typename NodeTypes::WeakAnd &addWeakAnd(int child_count, uint32_t minHits, const stringref & view) {
        return addIntermediate(createWeakAnd<NodeTypes>(minHits, view), child_count);
    }
    typename NodeTypes::Equiv &addEquiv(int child_count, int32_t id, Weight weight) {
        return addIntermediate(createEquiv<NodeTypes>(id, weight), child_count);
    }
    typename NodeTypes::Phrase &addPhrase(int child_count, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        typename NodeTypes::Phrase &node = addIntermediate(createPhrase<NodeTypes>(view, id, weight), child_count);
        setWeightOverride(weight);
        return node;
    }
    typename NodeTypes::SameElement &addSameElement(int child_count, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        typename NodeTypes::SameElement &node = addIntermediate(createSameElement<NodeTypes>(view, id, weight), child_count);
        setWeightOverride(weight);
        return node;
    }
    typename NodeTypes::WeightedSetTerm &addWeightedSetTerm( int child_count, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        typename NodeTypes::WeightedSetTerm &node = addIntermediate(createWeightedSetTerm<NodeTypes>(view, id, weight), child_count);
        return node;
    }
    typename NodeTypes::DotProduct &addDotProduct( int child_count, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        typename NodeTypes::DotProduct &node = addIntermediate( createDotProduct<NodeTypes>(view, id, weight), child_count);
        return node;
    }
    typename NodeTypes::WandTerm &addWandTerm(
            int child_count, const stringref &view,
            int32_t id, Weight weight, uint32_t targetNumHits,
            int64_t scoreThreshold, double thresholdBoostFactor)
    {
        adjustWeight(weight);
        typename NodeTypes::WandTerm &node = addIntermediate(
                createWandTerm<NodeTypes>(view, id, weight, targetNumHits, scoreThreshold, thresholdBoostFactor),
                child_count);
        return node;
    }
    typename NodeTypes::Rank &addRank(int child_count) {
        return addIntermediate(createRank<NodeTypes>(), child_count);
    }

    typename NodeTypes::NumberTerm &addNumberTerm(const stringref &term, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createNumberTerm<NodeTypes>(term, view, id, weight));
    }
    typename NodeTypes::PrefixTerm &addPrefixTerm(const stringref &term, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createPrefixTerm<NodeTypes>(term, view, id, weight));
    }
    typename NodeTypes::RangeTerm &addRangeTerm(const Range &range, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createRangeTerm<NodeTypes>(range, view, id, weight));
    }
    typename NodeTypes::StringTerm &addStringTerm(const stringref &term, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createStringTerm<NodeTypes>(term, view, id, weight));
    }
    typename NodeTypes::SubstringTerm &addSubstringTerm(const stringref &t, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createSubstringTerm<NodeTypes>(t, view, id, weight));
    }
    typename NodeTypes::SuffixTerm &addSuffixTerm(const stringref &term, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createSuffixTerm<NodeTypes>(term, view, id, weight));
    }
    typename NodeTypes::LocationTerm &addLocationTerm(const Location &loc, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createLocationTerm<NodeTypes>(loc, view, id, weight));
    }
    typename NodeTypes::PredicateQuery &addPredicateQuery(PredicateQueryTerm::UP term, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createPredicateQuery<NodeTypes>(std::move(term), view, id, weight));
    }
    typename NodeTypes::RegExpTerm &addRegExpTerm(const stringref &term, const stringref &view, int32_t id, Weight weight) {
        adjustWeight(weight);
        return addTerm(createRegExpTerm<NodeTypes>(term, view, id, weight));
    }
};

}
