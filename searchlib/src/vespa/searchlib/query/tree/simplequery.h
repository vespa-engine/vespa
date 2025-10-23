// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    SimpleNear(size_t dist, size_t num_negative_terms, size_t exclusion_distance)
      : Near(dist, num_negative_terms, exclusion_distance) {}
    ~SimpleNear() override;
};
struct SimpleONear : ONear {
    SimpleONear(size_t dist, size_t num_negative_terms, size_t exclusion_distance)
      : ONear(dist, num_negative_terms, exclusion_distance) {}
    ~SimpleONear() override;
};
struct SimpleOr : Or
{
    ~SimpleOr() override;
};
struct SimpleWeakAnd : WeakAnd {
    SimpleWeakAnd(uint32_t targetNumHits, std::string view) :
        WeakAnd(targetNumHits, std::string(std::move(view)))
    {}
    ~SimpleWeakAnd() override;
};
struct SimpleEquiv : Equiv {
    SimpleEquiv(int32_t id, Weight weight)
        : Equiv(id, weight) {}
    ~SimpleEquiv() override;
};
struct SimplePhrase : Phrase {
    SimplePhrase(std::string view, int32_t id, Weight weight)
        : Phrase(std::move(view), id, weight) {}
    ~SimplePhrase() override;
};

struct SimpleSameElement : SameElement {
    SimpleSameElement(std::string view, int32_t id, Weight weight)
        : SameElement(std::move(view), id, weight) {}
    ~SimpleSameElement() override;
};
struct SimpleWeightedSetTerm : WeightedSetTerm {
    using WeightedSetTerm::WeightedSetTerm;
    ~SimpleWeightedSetTerm() override;
};
struct SimpleDotProduct : DotProduct {
    using DotProduct::DotProduct;
    ~SimpleDotProduct() override;
};
struct SimpleWandTerm : WandTerm {
    using WandTerm::WandTerm;
    ~SimpleWandTerm() override;
};
struct SimpleInTerm : InTerm {
    using InTerm::InTerm;
    ~SimpleInTerm() override;
};
struct SimpleWordAlternatives : WordAlternatives {
    using WordAlternatives::WordAlternatives;
    ~SimpleWordAlternatives() override;
};
struct SimpleRank : Rank
{
    ~SimpleRank() override;
};
struct SimpleNumberTerm : NumberTerm {
    SimpleNumberTerm(Type term, std::string view, int32_t id, Weight weight)
        : NumberTerm(term, std::move(view), id, weight) {
    }
    ~SimpleNumberTerm() override;
};
struct SimpleLocationTerm : LocationTerm {
    SimpleLocationTerm(const Type &term, std::string view, int32_t id, Weight weight)
        : LocationTerm(term, std::move(view), id, weight) {
    }
    ~SimpleLocationTerm() override;
};
struct SimplePrefixTerm : PrefixTerm {
    SimplePrefixTerm(const Type &term, std::string view, int32_t id, Weight weight)
        : PrefixTerm(term, std::move(view), id, weight) {
    }
    ~SimplePrefixTerm() override;
};
struct SimpleRangeTerm : RangeTerm {
    SimpleRangeTerm(const Type &term, std::string view, int32_t id, Weight weight)
        : RangeTerm(term, std::move(view), id, weight) {
    }
    ~SimpleRangeTerm() override;
};
struct SimpleStringTerm : StringTerm {
    SimpleStringTerm(const Type &term, std::string view, int32_t id, Weight weight)
        : StringTerm(term, std::move(view), id, weight) {
    }
    ~SimpleStringTerm() override;
};
struct SimpleSubstringTerm : SubstringTerm {
    SimpleSubstringTerm(const Type &term, std::string view, int32_t id, Weight weight)
        : SubstringTerm(term, std::move(view), id, weight) {
    }
    ~SimpleSubstringTerm() override;
};
struct SimpleSuffixTerm : SuffixTerm {
    SimpleSuffixTerm(const Type &term, std::string view, int32_t id, Weight weight)
        : SuffixTerm(term, std::move(view), id, weight) {
    }
    ~SimpleSuffixTerm() override;
};
struct SimplePredicateQuery : PredicateQuery {
    SimplePredicateQuery(PredicateQueryTerm::UP term, std::string view, int32_t id, Weight weight)
        : PredicateQuery(std::move(term), std::move(view), id, weight) {
    }
    ~SimplePredicateQuery() override;
};
struct SimpleRegExpTerm : RegExpTerm {
    SimpleRegExpTerm(const Type &term, std::string view, int32_t id, Weight weight)
        : RegExpTerm(term, std::move(view), id, weight) {
    }
    ~SimpleRegExpTerm() override;
};
struct SimpleNearestNeighborTerm : NearestNeighborTerm {
    SimpleNearestNeighborTerm(std::string_view query_tensor_name, std::string field_name,
                              int32_t id, Weight weight, uint32_t target_num_hits,
                              bool allow_approximate, uint32_t explore_additional_hits,
                              double distance_threshold)
        : NearestNeighborTerm(query_tensor_name, std::move(field_name), id, weight,
                              target_num_hits, allow_approximate, explore_additional_hits,
                              distance_threshold)
    {}
    ~SimpleNearestNeighborTerm() override;
};
struct SimpleFuzzyTerm : FuzzyTerm {
    SimpleFuzzyTerm(const Type &term, std::string view, int32_t id, Weight weight, uint32_t max_edit_distance,
                    uint32_t prefix_lock_length, bool prefix_match)
        : FuzzyTerm(term, std::move(view), id, weight, max_edit_distance, prefix_lock_length, prefix_match)
    {
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
    using InTerm = SimpleInTerm;
    using WordAlternatives = SimpleWordAlternatives;
};

}
