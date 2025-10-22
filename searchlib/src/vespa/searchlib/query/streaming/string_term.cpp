// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_term.h"
#include "query_visitor.h"

namespace search::streaming {

StringTerm::~StringTerm() = default;

void
StringTerm::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
