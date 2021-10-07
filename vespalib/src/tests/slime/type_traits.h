// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    typedef bool PassType;
    typedef PassType StoreType;
    static const bool unsetValue = false;
};

template<> struct TypeTraits<LONG> {
    typedef int64_t PassType;
    typedef PassType StoreType;
    static const int64_t unsetValue = 0;
};

template<> struct TypeTraits<DOUBLE> {
    typedef double PassType;
    typedef PassType StoreType;
    static const double unsetValue;
};

template<> struct TypeTraits<STRING> {
    typedef Memory PassType;
    static const Memory unsetValue;
};

template<> struct TypeTraits<DATA> {
    typedef Memory PassType;
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

