// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "predicate_query.h"
#include "query_visitor.h"

namespace search::streaming {

PredicateQuery::~PredicateQuery() = default;

void
PredicateQuery::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
