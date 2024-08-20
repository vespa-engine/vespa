// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <span>

namespace vespalib {

// const-cast for array references; use with care
template <typename T>
std::span<T> unconstify(const std::span<const T>& ref) {
    return std::span<T>(const_cast<T*>(ref.data()), ref.size());
}

}
