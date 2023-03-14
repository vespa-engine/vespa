// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/util/array.h>
#include <vector>
#include <cstdint>

namespace vespalib {

template <typename T>
Serializer &
Serializer::operator << (const std::vector<T> & v) {
    uint32_t sz(v.size());
    put(sz);
    for(size_t i(0); i < sz; i++) {
        (*this) << v[i];
    }
    return *this;
}

}

