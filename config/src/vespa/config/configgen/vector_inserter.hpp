// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "vector_inserter.h"

namespace config::internal {

template<typename V, typename Converter>
VectorInserter<V, Converter>::VectorInserter(V & vector)
    : _vector(vector)
{}

template<typename V, typename Converter>
void
VectorInserter<V, Converter>::entry(size_t idx, const ::vespalib::slime::Inspector & inspector)
{
    (void) idx;
    Converter converter;
    _vector.push_back(converter(inspector));
}

}
