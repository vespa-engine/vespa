// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * This file defines a set of subclasses to the query nodes, and a
 * traits class to make them easy to use with the query builder. These
 * subclasses don't add any extra information to the abstract nodes.
 */

#pragma once

#include "intermediatenodes.h"
#include "termnodes.h"

namespace search::query {

struct SimpleAnd : And {};
struct SimpleAndNot : AndNot {};
struct SimpleNear : Near { SimpleNear(size_t dist) : Near(dist) {} };
struct SimpleONear : ONear { SimpleONear(size_t dist) : ONear(dist) {} };
struct SimpleOr : Or {};
struct SimpleWeakAnd : WeakAnd {
    SimpleWeakAnd(uint32_t minHits, vespalib::stringref view) :
        WeakAnd(minHits, view)
    {}
};
struct SimpleEquiv : Equiv {
    SimpleEquiv(int32_t id, Weight weight)
        : Equiv(id, weight) {}
};
struct SimplePhrase : Phrase {
    SimplePhrase(vespalib::stringref view, int32_t id, Weight weight)
        : Phrase(view, id, weight) {}
};

struct SimpleSameElement : SameElement {
    SimpleSameElement(vespalib::stringref view) : SameElement(view) {}
};
struct SimpleWeightedSetTerm : WeightedSetTerm {
    SimpleWeightedSetTerm(vespalib::stringref view, int32_t id, Weight weight)
        : WeightedSetTerm(view, id, weight) {}
};
struct SimpleDotProduct : DotProduct {
    SimpleDotProduct(vespalib::stringref view, int32_t id, Weight weight)
        : DotProduct(view, id, weight) {}
};
struct SimpleWandTerm : WandTerm {
    SimpleWandTerm(vespalib::stringref view, int32_t id, Weight weight,
                   uint32_t targetNumHits, int64_t scoreThreshold, double thresholdBoostFactor)
        : WandTerm(view, id, weight, targetNumHits, scoreThreshold, thresholdBoostFactor) {}
};
struct SimpleRank : Rank {};
struct SimpleNumberTerm : NumberTerm {
    SimpleNumberTerm(Type term, vespalib::stringref view,
                    int32_t id, Weight weight)
        : NumberTerm(term, view, id, weight) {
    }
};
struct SimpleLocationTerm : LocationTerm {
    SimpleLocationTerm(const Type &term, vespalib::stringref view,
                       int32_t id, Weight weight)
        : LocationTerm(term, view, id, weight) {
    }
};
struct SimplePrefixTerm : PrefixTerm {
    SimplePrefixTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight)
        : PrefixTerm(term, view, id, weight) {
    }
};
struct SimpleRangeTerm : RangeTerm {
    SimpleRangeTerm(const Type &term, vespalib::stringref view,
                    int32_t id, Weight weight)
        : RangeTerm(term, view, id, weight) {
    }
};
struct SimpleStringTerm : StringTerm {
    SimpleStringTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight)
        : StringTerm(term, view, id, weight) {
    }
};
struct SimpleSubstringTerm : SubstringTerm {
    SimpleSubstringTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight)
        : SubstringTerm(term, view, id, weight) {
    }
};
struct SimpleSuffixTerm : SuffixTerm {
    SimpleSuffixTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight)
        : SuffixTerm(term, view, id, weight) {
    }
};
struct SimplePredicateQuery : PredicateQuery {
    SimplePredicateQuery(PredicateQueryTerm::UP term,
                         vespalib::stringref view,
                         int32_t id, Weight weight)
        : PredicateQuery(std::move(term), view, id, weight) {
    }
};
struct SimpleRegExpTerm : RegExpTerm {
    SimpleRegExpTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight)
        : RegExpTerm(term, view, id, weight) {
    }
};
struct SimpleNearestNeighborTerm : NearestNeighborTerm {
    SimpleNearestNeighborTerm(vespalib::stringref query_tensor_name, vespalib::stringref field_name,
                              int32_t id, Weight weight, uint32_t target_num_hits,
                              bool allow_approximate, uint32_t explore_additional_hits,
                              double distance_threshold)
        : NearestNeighborTerm(query_tensor_name, field_name, id, weight,
                              target_num_hits, allow_approximate, explore_additional_hits,
                              distance_threshold)
    {}
};


struct SimpleQueryNodeTypes {
    using And = SimpleAnd;
    using AndNot = SimpleAndNot;
    using Equiv = SimpleEquiv;
    using NumberTerm = SimpleNumberTerm;
    using LocationTerm = SimpleLocationTerm;
    using Near = SimpleNear;
    using ONear = SimpleONear;
    using Or = SimpleOr;
    using Phrase = SimplePhrase;
    using SameElement = SimpleSameElement;
    using PrefixTerm = SimplePrefixTerm;
    using RangeTerm = SimpleRangeTerm;
    using Rank = SimpleRank;
    using StringTerm = SimpleStringTerm;
    using SubstringTerm = SimpleSubstringTerm;
    using SuffixTerm = SimpleSuffixTerm;
    using WeakAnd = SimpleWeakAnd;
    using WeightedSetTerm = SimpleWeightedSetTerm;
    using DotProduct = SimpleDotProduct;
    using WandTerm = SimpleWandTerm;
    using PredicateQuery = SimplePredicateQuery;
    using RegExpTerm = SimpleRegExpTerm;
    using NearestNeighborTerm = SimpleNearestNeighborTerm;
};

}
