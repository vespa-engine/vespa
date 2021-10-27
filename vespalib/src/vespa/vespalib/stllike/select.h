// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

namespace vespalib {

// Convenience functor for extracting the first element of a std::pair (or compatible type)
template <typename Pair>
struct Select1st {
    constexpr typename Pair::first_type& operator()(Pair& p) const noexcept {
        return p.first;
    }
    constexpr const typename Pair::first_type& operator()(const Pair& p) const noexcept {
        return p.first;
    }
};

}
