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
    using Near::Near;
    ~SimpleNear() override;
};
struct SimpleONear : ONear {
    using ONear::ONear;
    ~SimpleONear() override;
};
struct SimpleOr : Or {
    ~SimpleOr() override;
};
struct SimpleWeakAnd : WeakAnd {
    using WeakAnd::WeakAnd;
    ~SimpleWeakAnd() override;
};
struct SimpleEquiv : Equiv {
    using Equiv::Equiv;
    ~SimpleEquiv() override;
};
struct SimplePhrase : Phrase {
    using Phrase::Phrase;
    ~SimplePhrase() override;
};

struct SimpleSameElement : SameElement {
    using SameElement::SameElement;
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
struct SimpleRank : Rank {
    ~SimpleRank() override;
};
struct SimpleLabelWrapper : LabelWrapper {
    using LabelWrapper::LabelWrapper;
    ~SimpleLabelWrapper() override;
};
struct SimpleNumberTerm : NumberTerm {
    using NumberTerm::NumberTerm;
    ~SimpleNumberTerm() override;
};
struct SimpleLocationTerm : LocationTerm {
    using LocationTerm::LocationTerm;
    ~SimpleLocationTerm() override;
};
struct SimplePrefixTerm : PrefixTerm {
    using PrefixTerm::PrefixTerm;
    ~SimplePrefixTerm() override;
};
struct SimpleRangeTerm : RangeTerm {
    using RangeTerm::RangeTerm;
    ~SimpleRangeTerm() override;
};
struct SimpleStringRangeTerm : StringRangeTerm {
    using StringRangeTerm::StringRangeTerm;
    ~SimpleStringRangeTerm() override;
};
struct SimpleStringTerm : StringTerm {
    using StringTerm::StringTerm;
    ~SimpleStringTerm() override;
};
struct SimpleSubstringTerm : SubstringTerm {
    using SubstringTerm::SubstringTerm;
    ~SimpleSubstringTerm() override;
};
struct SimpleSuffixTerm : SuffixTerm {
    using SuffixTerm::SuffixTerm;
    ~SimpleSuffixTerm() override;
};
struct SimplePredicateQuery : PredicateQuery {
    using PredicateQuery::PredicateQuery;
    ~SimplePredicateQuery() override;
};
struct SimpleRegExpTerm : RegExpTerm {
    using RegExpTerm::RegExpTerm;
    ~SimpleRegExpTerm() override;
};
struct SimpleNearestNeighborTerm : NearestNeighborTerm {
    using NearestNeighborTerm::NearestNeighborTerm;
    ~SimpleNearestNeighborTerm() override;
};
struct SimpleSparseTensorWandTerm : SparseTensorWandTerm {
    using SparseTensorWandTerm::SparseTensorWandTerm;
    ~SimpleSparseTensorWandTerm() override;
};
struct SimpleFuzzyTerm : FuzzyTerm {
    using FuzzyTerm::FuzzyTerm;
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
    using StringRangeTerm = SimpleStringRangeTerm;
    using Rank = SimpleRank;
    using LabelWrapper = SimpleLabelWrapper;
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
    using SparseTensorWandTerm = SimpleSparseTensorWandTerm;
    using FuzzyTerm = SimpleFuzzyTerm;
    using InTerm = SimpleInTerm;
    using WordAlternatives = SimpleWordAlternatives;
};

} // namespace search::query
