// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "spanlist.h"

namespace document {

SpanList::~SpanList() {
    for (size_t i = 0; i < _span_vector.size(); ++i) {
        delete _span_vector[i];
    }
}

SimpleSpanList::SimpleSpanList(size_t sz) :
    _span_vector(sz)
{
}

SimpleSpanList::~SimpleSpanList()
{
}

}  // namespace document
