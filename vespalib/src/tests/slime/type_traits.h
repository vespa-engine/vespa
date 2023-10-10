// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/data/slime/type.h>
#include <vespa/vespalib/data/memory.h>

namespace vespalib {
namespace slime {

// internal traits for data type types
template<typename T> struct TypeTraits {};

template<> struct TypeTraits<NIX> {
    static void assertIsCreatedEmpty() {}
};

template<> struct TypeTraits<BOOL> {
    using PassType = bool;
    using StoreType = PassType;
    static const bool unsetValue = false;
};

template<> struct TypeTraits<LONG> {
    using PassType = int64_t;
    using StoreType = PassType;
    static const int64_t unsetValue = 0;
};

template<> struct TypeTraits<DOUBLE> {
    using PassType = double;
    using StoreType = PassType;
    static const double unsetValue;
};

template<> struct TypeTraits<STRING> {
    using PassType = Memory;
    static const Memory unsetValue;
};

template<> struct TypeTraits<DATA> {
    using PassType = Memory;
    static const Memory unsetValue;
};

template<> struct TypeTraits<ARRAY> {
    static void assertIsCreatedEmpty() {}
};

template<> struct TypeTraits<OBJECT> {
    static void assertIsCreatedEmpty() {}
};

} // namespace vespalib::slime
} // namespace vespalib

