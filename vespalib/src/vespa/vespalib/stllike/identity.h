// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <utility>

namespace vespalib {

// Functor which returns its argument unchanged.
// Functionally identical to C++20's std::identity
// TODO remove and replace with std::identity once it is available.
struct Identity {
    template <typename T>
    constexpr T&& operator()(T&& v) const noexcept {
        return std::forward<T>(v);
    }
};

}
