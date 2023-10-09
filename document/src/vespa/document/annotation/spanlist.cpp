// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "spanlist.h"
#include "spantreevisitor.h"

namespace document {

SpanList::~SpanList() {
    for (size_t i = 0; i < _span_vector.size(); ++i) {
        delete _span_vector[i];
    }
}

void SpanList::accept(SpanTreeVisitor &visitor) const {
    visitor.visit(*this);
}

SimpleSpanList::SimpleSpanList(size_t sz) :
    _span_vector(sz)
{
}

SimpleSpanList::~SimpleSpanList()
{
}

void SimpleSpanList::accept(SpanTreeVisitor &visitor) const {
    visitor.visit(*this);
}

}  // namespace document
