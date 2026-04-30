// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/fast_range.h>
#include <vespa/vespalib/util/xoshiro.h>
#include <gtest/gtest.h>
#include <algorithm>
#include <concepts>
#include <random>

using namespace ::testing;

namespace vespalib {

namespace {

template <std::integral T>
void do_test_fast_range() {
    Xoshiro256PlusPlusPrng gen(std::random_device{}());
    for (size_t i = 0; i < 10'000; ++i) {
        const T max_excl = std::max(static_cast<T>(gen()), T{1});
        const T mapped   = map_random_to_range(static_cast<T>(gen()), max_excl);

        ASSERT_LT(mapped, max_excl) << mapped << " for range [0, " << max_excl << ")";
    }
}

template <std::unsigned_integral T>
void do_test_next_random_in_range() {
    Xoshiro256PlusPlusPrng gen(std::random_device{}());
    for (size_t i = 0; i < 10'000; ++i) {
        T from_incl = static_cast<T>(gen());
        T to_excl   = static_cast<T>(gen());
        if (from_incl > to_excl) {
            std::swap(from_incl, to_excl);
        } else if (from_incl == to_excl) {
            continue; // we'll get them next time, for sure...!
        }
        T val = next_random_in_range<T>(gen, from_incl, to_excl);

        ASSERT_GE(val, from_incl);
        ASSERT_LT(val, to_excl);
    }
}

} // namespace

TEST(FastRangeTest, can_map_integers_to_ranges) {
    do_test_fast_range<uint8_t>();
    do_test_fast_range<uint16_t>();
    do_test_fast_range<uint32_t>();
    do_test_fast_range<uint64_t>();
}

TEST(FastRangeTest, can_generate_randoms_in_numeric_ranges) {
    do_test_next_random_in_range<uint8_t>();
    do_test_next_random_in_range<uint16_t>();
    do_test_next_random_in_range<uint32_t>();
    do_test_next_random_in_range<uint64_t>();
}

} // namespace vespalib
