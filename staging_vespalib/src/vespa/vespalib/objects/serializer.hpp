// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/fieldbase.h>
#include <vespa/vespalib/util/array.h>
#include <vector>
#include <stdint.h>

namespace vespalib {

template <typename T>
Serializer &
Serializer::operator << (const vespalib::Array<T> & v) {
    uint32_t sz(v.size());
    put(_sizeField, sz);
    for(size_t i(0); i < sz; i++) {
        (*this) << v[i];
    }
    return *this;
}
template <typename T>
Serializer &
Serializer::operator << (const std::vector<T> & v) {
    uint32_t sz(v.size());
    put(_sizeField, sz);
    for(size_t i(0); i < sz; i++) {
        (*this) << v[i];
    }
    return *this;
}

}

