// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <random>
#include <vector>

namespace vespalib::hwaccelerated {

template <typename T>
constexpr auto choose_distribution() noexcept {
    // All supported element types have a well-defined range of at least [-128, 127].
    return std::uniform_int_distribution(-128, 127); // Closed interval
}

template <>
constexpr auto choose_distribution<uint8_t>() noexcept {
    return std::uniform_int_distribution<uint8_t>(0, 255);
}

template <>
constexpr auto choose_distribution<size_t>() noexcept {
    // size_t is used for popcount, in which case we want to spray and pray across all bits.
    return std::uniform_int_distribution<size_t>(0ULL, UINT64_MAX);
}

template <typename T, std::uniform_random_bit_generator Rng, typename AcceptFn>
std::vector<T> create_and_fill(Rng& rng, size_t sz, AcceptFn accept_fn) {
    auto dist = choose_distribution<T>();
    std::vector<T> v(sz);
    for (size_t i(0); i < sz; i++) {
        T candidate = dist(rng);
        while (!accept_fn(candidate)) {
            candidate = dist(rng);
        }
        v[i] = candidate;
    }
    return v;
}

template <typename T, typename AcceptFn>
std::pair<std::vector<T>, std::vector<T>>
create_and_fill_lhs_rhs(size_t sz, AcceptFn accept_fn) {
    std::minstd_rand prng;
    prng.seed(1234567);
    std::vector<T> a = create_and_fill<T>(prng, sz, accept_fn);
    std::vector<T> b = create_and_fill<T>(prng, sz, accept_fn);
    return {std::move(a), std::move(b)};
}

struct AlwaysAccept {
    template <typename T>
    bool operator()(const T&) const noexcept { return true; }
};

template <typename T>
std::pair<std::vector<T>, std::vector<T>>
create_and_fill_lhs_rhs(size_t sz) {
    return create_and_fill_lhs_rhs<T>(sz, AlwaysAccept{});
}

}
