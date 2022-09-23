// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <type_traits>

namespace vespalib {

//-----------------------------------------------------------------------------

template <typename T>
inline constexpr bool enable_skip_destruction = false;

template <typename T>
concept can_skip_destruction = std::is_trivially_destructible_v<T> || enable_skip_destruction<T>;

// Macro used to indicate that it is safe to skip destruction of
// objects of class T. This macro can only be used in the global
// namespace. This macro will typically be used to tag classes that do
// not classify as trivially destructible because they inherit an
// empty virtual destructor.
#define VESPA_CAN_SKIP_DESTRUCTION(MyType)                     \
    namespace vespalib {                                       \
        template <>                                            \
        inline constexpr bool enable_skip_destruction<MyType> = true; \
    }

//-----------------------------------------------------------------------------

template <typename T>
concept has_type_type = requires { typename T::type; };

//-----------------------------------------------------------------------------

} // namespace vespalib
