// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <type_traits>

namespace vespalib {

//-----------------------------------------------------------------------------

namespace is_copyable_magic {
template <typename T> static const T& makeConstRef();
template <typename T> static decltype(T(makeConstRef<T>()), 'y') check(int);
template <typename ...> static int check(...);
}

template <typename T>
struct is_copyable : std::integral_constant<bool, (sizeof(is_copyable_magic::check<T>(0)) == sizeof('y'))> {};

//-----------------------------------------------------------------------------

struct void_tag { void_tag() = delete; };

//-----------------------------------------------------------------------------

template <typename T>
struct can_skip_destruction : std::is_trivially_destructible<T> {};

// Macro used to indicate that it is safe to skip destruction of
// objects of class T. This macro can only be used in the global
// namespace. This macro will typically be used to tag classes that do
// not classify as trivially destructible because they inherit an
// empty virtual destructor.
#define VESPA_CAN_SKIP_DESTRUCTION(T)                   \
    namespace vespalib {                                \
    template <>                                         \
    struct can_skip_destruction<T> : std::true_type {}; \
    }

//-----------------------------------------------------------------------------

} // namespace vespalib
