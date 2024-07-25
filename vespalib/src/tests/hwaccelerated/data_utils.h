// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <random>
#include <vector>

namespace vespalib::hwaccelerated {

template <typename T, std::uniform_random_bit_generator Rng>
std::vector<T> create_and_fill(Rng& rng, size_t sz) {
    // All supported types have a well-defined range of at least [-128, 127].
    std::uniform_int_distribution<> dist(-128, 127); // Closed interval
    std::vector<T> v(sz);
    for (size_t i(0); i < sz; i++) {
        v[i] = dist(rng);
    }
    return v;
}

template <typename T>
std::pair<std::vector<T>, std::vector<T>>
create_and_fill_lhs_rhs(size_t sz) {
    std::minstd_rand prng;
    prng.seed(1234567);
    std::vector<T> a = create_and_fill<T>(prng, sz);
    std::vector<T> b = create_and_fill<T>(prng, sz);
    return {std::move(a), std::move(b)};
}

}
