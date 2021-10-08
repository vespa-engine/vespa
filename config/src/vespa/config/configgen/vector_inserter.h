// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "value_converter.h"
#include <vespa/vespalib/data/slime/array_traverser.h>
#include <vespa/vespalib/stllike/string.h>

namespace config {

namespace internal {

template<typename T, typename Converter = ::config::internal::ValueConverter<T> >
class VectorInserter : public ::vespalib::slime::ArrayTraverser {
public:
    VectorInserter(std::vector<T> & vector);
    void entry(size_t idx, const ::vespalib::slime::Inspector & inspector) override;
private:
    std::vector<T> & _vector;
};

} // namespace internal

} // namespace config

#include "vector_inserter.hpp"

