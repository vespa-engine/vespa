// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "number_term.h"
#include "query_visitor.h"

namespace search::streaming {

NumberTerm::~NumberTerm() = default;

void
NumberTerm::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
