// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "value_converter.h"
#include <vespa/vespalib/data/slime/array_traverser.h>
#include <vespa/vespalib/stllike/string.h>

namespace config::internal {

template<typename V, typename Converter = ::config::internal::ValueConverter<typename V::value_type> >
class VectorInserter : public ::vespalib::slime::ArrayTraverser {
public:
    VectorInserter(V & vector);
    void entry(size_t idx, const ::vespalib::slime::Inspector & inspector) override;
private:
    V & _vector;
};

}
