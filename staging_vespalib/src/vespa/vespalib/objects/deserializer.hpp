// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "deserializer.h"

namespace vespalib {

template <typename T>
Deserializer &
Deserializer::operator >> (vespalib::Array<T> & v) {
    uint32_t sz;
    get(sz);
    v.resize(sz);
    for(size_t i(0); i < sz; i++) {
        (*this) >> v[i];
    }
    return *this;
}

template <typename T>
Deserializer &
Deserializer::operator >> (std::vector<T> & v) {
    uint32_t sz;
    get(sz);
    v.resize(sz);
    for(size_t i(0); i < sz; i++) {
        (*this) >> v[i];
    }
    return *this;
}

}

