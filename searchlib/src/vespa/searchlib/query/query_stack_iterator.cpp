// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "query_stack_iterator.h"
#include <vespa/searchlib/query/tree/integer_term_vector.h>
#include <vespa/searchlib/query/tree/predicate_query_term.h>
#include <vespa/searchlib/query/tree/string_term_vector.h>

using search::query::IntegerTermVector;
using search::query::PredicateQueryTerm;
using search::query::StringTermVector;

namespace search {

std::unique_ptr<query::PredicateQueryTerm> QueryStackIterator::getPredicateQueryTerm() {
    return std::move(_d.predicateQueryTerm);
}
std::unique_ptr<query::TermVector> QueryStackIterator::get_terms() {
    return std::move(_d.termVector);
}

std::string_view QueryStackIterator::DEFAULT_INDEX = "default";

QueryStackIterator::~QueryStackIterator() = default;

}
