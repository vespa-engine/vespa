// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "suffix_term.h"
#include "query_visitor.h"

namespace search::streaming {

SuffixTerm::~SuffixTerm() = default;

void
SuffixTerm::accept(QueryVisitor &visitor) {
    visitor.visit(*this);
}

}
