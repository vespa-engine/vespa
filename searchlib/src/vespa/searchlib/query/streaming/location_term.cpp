// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "location_term.h"
#include "query_visitor.h"

namespace search::streaming {

LocationTerm::~LocationTerm() = default;

void
LocationTerm::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
