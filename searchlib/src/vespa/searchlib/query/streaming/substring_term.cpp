// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "substring_term.h"
#include "query_visitor.h"

namespace search::streaming {

SubstringTerm::~SubstringTerm() = default;

void
SubstringTerm::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
