// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/xoshiro.h>

#include <algorithm>
#include <concepts>
#include <random>
#include <ranges>
#include <vector>

namespace vespalib::hwaccelerated {

template <typename T> constexpr auto choose_distribution() noexcept {
    // All supported element types have a well-defined range of at least [-128, 127].
    return std::uniform_int_distribution(-128, 127); // Closed interval
}

template <> constexpr auto choose_distribution<size_t>() noexcept {
    // size_t is used for popcount, in which case we want to spray and pray across all bits.
    return std::uniform_int_distribution<size_t>(0ULL, UINT64_MAX);
}

template <typename T, std::uniform_random_bit_generator Rng>
std::vector<T> create_and_fill(Rng& rng, size_t sz) {
    auto           dist = choose_distribution<T>();
    std::vector<T> v(sz);
    for (size_t i(0); i < sz; i++) {
        v[i] = dist(rng);
    }
    return v;
}

template <std::floating_point T, std::uniform_random_bit_generator Rng>
std::vector<T> create_and_fill_float(Rng& rng, size_t sz) {
    std::uniform_real_distribution<T> dist(-1, 1);
    std::vector<T>                    v(sz);
    std::ranges::generate(v, [&]() mutable { return dist(rng); });
    return v;
}

template <typename T> std::pair<std::vector<T>, std::vector<T>> create_and_fill_lhs_rhs(size_t sz) {
    Xoshiro256PlusPlusPrng prng(1234567);

    std::vector<T> a = create_and_fill<T>(prng, sz);
    std::vector<T> b = create_and_fill<T>(prng, sz);
    return {std::move(a), std::move(b)};
}

} // namespace vespalib::hwaccelerated
