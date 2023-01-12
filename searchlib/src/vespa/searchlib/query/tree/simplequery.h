// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/*
 * This file defines a set of subclasses to the query nodes, and a
 * traits class to make them easy to use with the query builder. These
 * subclasses don't add any extra information to the abstract nodes.
 */

#pragma once

#include "const_bool_nodes.h"
#include "intermediatenodes.h"
#include "termnodes.h"

namespace search::query {

struct SimpleTrue : TrueQueryNode {
    ~SimpleTrue() override;
};
struct SimpleFalse : FalseQueryNode {
    ~SimpleFalse() override;
};
struct SimpleAnd : And {
    ~SimpleAnd() override;
};
struct SimpleAndNot : AndNot {
    ~SimpleAndNot() override;
};
struct SimpleNear : Near {
    SimpleNear(size_t dist) : Near(dist) {}
    ~SimpleNear() override;
};
struct SimpleONear : ONear {
    SimpleONear(size_t dist) : ONear(dist) {}
    ~SimpleONear() override;
};
struct SimpleOr : Or
{
    ~SimpleOr() override;
};
struct SimpleWeakAnd : WeakAnd {
    SimpleWeakAnd(uint32_t minHits, vespalib::stringref view) :
        WeakAnd(minHits, view)
    {}
};
struct SimpleEquiv : Equiv {
    SimpleEquiv(int32_t id, Weight weight)
        : Equiv(id, weight) {}
    ~SimpleEquiv() override;
};
struct SimplePhrase : Phrase {
    SimplePhrase(vespalib::stringref view, int32_t id, Weight weight)
        : Phrase(view, id, weight) {}
    ~SimplePhrase() override;
};

struct SimpleSameElement : SameElement {
    SimpleSameElement(vespalib::stringref view, int32_t id, Weight weight)
        : SameElement(view, id, weight) {}
    ~SimpleSameElement() override;
};
struct SimpleWeightedSetTerm : WeightedSetTerm {
    SimpleWeightedSetTerm(uint32_t num_terms, vespalib::stringref view, int32_t id, Weight weight)
        : WeightedSetTerm(num_terms, view, id, weight) {}
    ~SimpleWeightedSetTerm() override;
};
struct SimpleDotProduct : DotProduct {
    SimpleDotProduct(uint32_t num_terms, vespalib::stringref view, int32_t id, Weight weight)
        : DotProduct(num_terms, view, id, weight) {}
    ~SimpleDotProduct() override;
};
struct SimpleWandTerm : WandTerm {
    SimpleWandTerm(uint32_t num_terms, vespalib::stringref view, int32_t id, Weight weight,
                   uint32_t targetNumHits, int64_t scoreThreshold, double thresholdBoostFactor)
        : WandTerm(num_terms, view, id, weight, targetNumHits, scoreThreshold, thresholdBoostFactor) {}
    ~SimpleWandTerm() override;
};
struct SimpleRank : Rank
{
    ~SimpleRank() override;
};
struct SimpleNumberTerm : NumberTerm {
    SimpleNumberTerm(Type term, vespalib::stringref view,
                    int32_t id, Weight weight)
        : NumberTerm(term, view, id, weight) {
    }
    ~SimpleNumberTerm() override;
};
struct SimpleLocationTerm : LocationTerm {
    SimpleLocationTerm(const Type &term, vespalib::stringref view,
                       int32_t id, Weight weight)
        : LocationTerm(term, view, id, weight) {
    }
    ~SimpleLocationTerm() override;
};
struct SimplePrefixTerm : PrefixTerm {
    SimplePrefixTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight)
        : PrefixTerm(term, view, id, weight) {
    }
    ~SimplePrefixTerm() override;
};
struct SimpleRangeTerm : RangeTerm {
    SimpleRangeTerm(const Type &term, vespalib::stringref view,
                    int32_t id, Weight weight)
        : RangeTerm(term, view, id, weight) {
    }
    ~SimpleRangeTerm() override;
};
struct SimpleStringTerm : StringTerm {
    SimpleStringTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight)
        : StringTerm(term, view, id, weight) {
    }
    ~SimpleStringTerm() override;
};
struct SimpleSubstringTerm : SubstringTerm {
    SimpleSubstringTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight)
        : SubstringTerm(term, view, id, weight) {
    }
    ~SimpleSubstringTerm() override;
};
struct SimpleSuffixTerm : SuffixTerm {
    SimpleSuffixTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight)
        : SuffixTerm(term, view, id, weight) {
    }
    ~SimpleSuffixTerm() override;
};
struct SimplePredicateQuery : PredicateQuery {
    SimplePredicateQuery(PredicateQueryTerm::UP term,
                         vespalib::stringref view,
                         int32_t id, Weight weight)
        : PredicateQuery(std::move(term), view, id, weight) {
    }
    ~SimplePredicateQuery() override;
};
struct SimpleRegExpTerm : RegExpTerm {
    SimpleRegExpTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight)
        : RegExpTerm(term, view, id, weight) {
    }
    ~SimpleRegExpTerm() override;
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
    ~SimpleNearestNeighborTerm() override;
};
struct SimpleFuzzyTerm : FuzzyTerm {
    SimpleFuzzyTerm(const Type &term, vespalib::stringref view,
                     int32_t id, Weight weight,
                     uint32_t maxEditDistance, uint32_t prefixLength)
            : FuzzyTerm(term, view, id, weight, maxEditDistance, prefixLength) {
    }
    ~SimpleFuzzyTerm() override;
};

struct SimpleQueryNodeTypes {
    using And = SimpleAnd;
    using AndNot = SimpleAndNot;
    using Equiv = SimpleEquiv;
    using TrueQueryNode = SimpleTrue;
    using FalseQueryNode = SimpleFalse;
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
    using FuzzyTerm = SimpleFuzzyTerm;
};

}
