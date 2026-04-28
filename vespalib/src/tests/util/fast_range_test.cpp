// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/fast_range.h>
#include <vespa/vespalib/util/xoshiro.h>
#include <gtest/gtest.h>
#include <concepts>
#include <random>

using namespace ::testing;

namespace vespalib {

namespace {

template <std::integral T>
void do_test_fast_range() {
    Xoshiro256PlusPlusPrng gen(std::random_device{}());
    for (size_t i = 0; i < 10'000; ++i) {
        const T max_incl = static_cast<T>(gen());
        const T mapped   = map_random_to_range(static_cast<T>(gen()), max_incl);

        ASSERT_TRUE(mapped <= max_incl) << mapped << " for range [0, " << max_incl << "]";
    }
}

} // namespace

TEST(FastRangeTest, can_map_u32_values) {
    do_test_fast_range<uint32_t>();
}

TEST(FastRangeTest, can_map_u64_values) {
    do_test_fast_range<uint64_t>();
}

} // namespace vespalib
