// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "prefix_term.h"
#include "query_visitor.h"

namespace search::streaming {

PrefixTerm::~PrefixTerm() = default;

void
PrefixTerm::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
