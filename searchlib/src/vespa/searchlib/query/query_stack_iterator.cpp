// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_stack_iterator.h"
#include <vespa/searchlib/query/tree/integer_term_vector.h>
#include <vespa/searchlib/query/tree/predicate_query_term.h>
#include <vespa/searchlib/query/tree/string_term_vector.h>

using search::query::IntegerTermVector;
using search::query::PredicateQueryTerm;
using search::query::StringTermVector;

namespace search {

namespace {
struct Dummy : QueryStackIterator {
    bool next() override { return false; }
    ~Dummy();
};
Dummy::~Dummy() = default;
}

std::unique_ptr<query::PredicateQueryTerm> QueryStackIterator::getPredicateQueryTerm() {
    return std::move(_d.predicateQueryTerm);
}
std::unique_ptr<query::TermVector> QueryStackIterator::get_terms() {
    return std::move(_d.termVector);
}

std::string_view QueryStackIterator::DEFAULT_INDEX = "default";

QueryStackIterator::~QueryStackIterator() = default;

std::unique_ptr<QueryStackIterator> QueryStackIterator::dummy() {
    return std::make_unique<Dummy>();
}

void QueryStackIterator::Data::clear() {
    itemType = ParseItem::ItemType::ITEM_UNDEF;

    noRankFlag = false;
    noPositionDataFlag = false;
    creaFilterFlag = false;
    isSpecialTokenFlag = false;
    allowApproximateFlag = false;
    prefix_match_semantics_flag = false;

    weight = query::Weight(0);

    // XXX what about these?
    // predicateQueryTerm = {};
    // termVector = {};

    index_view = {};
    term_view = {};
    integerTerm = 0;

    distanceThreshold = 0;
    scoreThreshold = 0;
    thresholdBoostFactor = 0;

    uniqueId = 0;
    arity = 0;
    nearDistance = 0;
    targetHits = 0;
    exploreAdditionalHits = 0;
    fuzzy_max_edit_distance = 0;
    fuzzy_prefix_lock_length = 0;
}

}
