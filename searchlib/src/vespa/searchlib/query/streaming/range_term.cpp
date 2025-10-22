// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "range_term.h"
#include "query_visitor.h"

namespace search::streaming {

RangeTerm::~RangeTerm() = default;

void
RangeTerm::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
