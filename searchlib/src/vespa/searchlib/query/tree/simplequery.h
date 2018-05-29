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
    SimpleWeakAnd(uint32_t minHits, const vespalib::stringref & view) :
        WeakAnd(minHits, view)
    {}
};
struct SimpleEquiv : Equiv {
    SimpleEquiv(int32_t id, Weight weight)
        : Equiv(id, weight) {}
};
struct SimplePhrase : Phrase {
    SimplePhrase(const vespalib::stringref &view, int32_t id, Weight weight)
        : Phrase(view, id, weight) {}
};

struct SimpleSameElement : SameElement {
    SimpleSameElement(const vespalib::stringref &view) : SameElement(view) {}
};
struct SimpleWeightedSetTerm : WeightedSetTerm {
    SimpleWeightedSetTerm(const vespalib::stringref &view, int32_t id, Weight weight)
        : WeightedSetTerm(view, id, weight) {}
};
struct SimpleDotProduct : DotProduct {
    SimpleDotProduct(const vespalib::stringref &view, int32_t id, Weight weight)
        : DotProduct(view, id, weight) {}
};
struct SimpleWandTerm : WandTerm {
    SimpleWandTerm(const vespalib::stringref &view, int32_t id, Weight weight,
                   uint32_t targetNumHits, int64_t scoreThreshold, double thresholdBoostFactor)
        : WandTerm(view, id, weight, targetNumHits, scoreThreshold, thresholdBoostFactor) {}
};
struct SimpleRank : Rank {};
struct SimpleNumberTerm : NumberTerm {
    SimpleNumberTerm(Type term, const vespalib::stringref &view,
                    int32_t id, Weight weight)
        : NumberTerm(term, view, id, weight) {
    }
};
struct SimpleLocationTerm : LocationTerm {
    SimpleLocationTerm(const Type &term, const vespalib::stringref &view,
                       int32_t id, Weight weight)
        : LocationTerm(term, view, id, weight) {
    }
};
struct SimplePrefixTerm : PrefixTerm {
    SimplePrefixTerm(const Type &term, const vespalib::stringref &view,
                     int32_t id, Weight weight)
        : PrefixTerm(term, view, id, weight) {
    }
};
struct SimpleRangeTerm : RangeTerm {
    SimpleRangeTerm(const Type &term, const vespalib::stringref &view,
                    int32_t id, Weight weight)
        : RangeTerm(term, view, id, weight) {
    }
};
struct SimpleStringTerm : StringTerm {
    SimpleStringTerm(const Type &term, const vespalib::stringref &view,
                     int32_t id, Weight weight)
        : StringTerm(term, view, id, weight) {
    }
};
struct SimpleSubstringTerm : SubstringTerm {
    SimpleSubstringTerm(const Type &term, const vespalib::stringref &view,
                     int32_t id, Weight weight)
        : SubstringTerm(term, view, id, weight) {
    }
};
struct SimpleSuffixTerm : SuffixTerm {
    SimpleSuffixTerm(const Type &term, const vespalib::stringref &view,
                     int32_t id, Weight weight)
        : SuffixTerm(term, view, id, weight) {
    }
};
struct SimplePredicateQuery : PredicateQuery {
    SimplePredicateQuery(PredicateQueryTerm::UP term,
                         const vespalib::stringref &view,
                         int32_t id, Weight weight)
        : PredicateQuery(std::move(term), view, id, weight) {
    }
};
struct SimpleRegExpTerm : RegExpTerm {
    SimpleRegExpTerm(const Type &term, const vespalib::stringref &view,
                     int32_t id, Weight weight)
        : RegExpTerm(term, view, id, weight) {
    }
};


struct SimpleQueryNodeTypes {
    typedef SimpleAnd And;
    typedef SimpleAndNot AndNot;
    typedef SimpleEquiv Equiv;
    typedef SimpleNumberTerm NumberTerm;
    typedef SimpleLocationTerm LocationTerm;
    typedef SimpleNear Near;
    typedef SimpleONear ONear;
    typedef SimpleOr Or;
    typedef SimplePhrase Phrase;
    typedef SimpleSameElement SameElement;
    typedef SimplePrefixTerm PrefixTerm;
    typedef SimpleRangeTerm RangeTerm;
    typedef SimpleRank Rank;
    typedef SimpleStringTerm StringTerm;
    typedef SimpleSubstringTerm SubstringTerm;
    typedef SimpleSuffixTerm SuffixTerm;
    typedef SimpleWeakAnd WeakAnd;
    typedef SimpleWeightedSetTerm WeightedSetTerm;
    typedef SimpleDotProduct DotProduct;
    typedef SimpleWandTerm WandTerm;
    typedef SimplePredicateQuery PredicateQuery;
    typedef SimpleRegExpTerm RegExpTerm;
};

}
