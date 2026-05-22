// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stride.h>

#include <numeric>
#include <vector>

using vespalib::Stride;

namespace {

std::vector<uint32_t> collect(uint32_t distance, uint32_t steps) {
    Stride                s(distance, steps);
    std::vector<uint32_t> out;
    out.reserve(steps);
    for (uint32_t i = 0; i < steps; ++i) {
        out.push_back(s.next());
    }
    return out;
}

uint32_t sum(const std::vector<uint32_t>& v) {
    return std::accumulate(v.begin(), v.end(), uint32_t(0));
}

} // namespace

TEST(StrideTest, zero_distance_yields_only_zeros) {
    EXPECT_EQ(collect(0, 4), (std::vector<uint32_t>{0, 0, 0, 0}));
}

TEST(StrideTest, distance_is_distributed_evenly_when_divisible) {
    EXPECT_EQ(collect(10, 5), (std::vector<uint32_t>{2, 2, 2, 2, 2}));
}

TEST(StrideTest, remainder_is_spread_across_the_sequence) {
    EXPECT_EQ(collect(7, 3), (std::vector<uint32_t>{2, 2, 3}));
    EXPECT_EQ(collect(11, 4), (std::vector<uint32_t>{2, 3, 3, 3}));
}

TEST(StrideTest, distance_smaller_than_steps_yields_zeros_and_ones) {
    EXPECT_EQ(collect(3, 10), (std::vector<uint32_t>{0, 0, 0, 1, 0, 0, 1, 0, 0, 1}));
    EXPECT_EQ(collect(1, 5), (std::vector<uint32_t>{0, 0, 0, 0, 1}));
}

TEST(StrideTest, single_step_returns_full_distance) {
    EXPECT_EQ(collect(42, 1), (std::vector<uint32_t>{42}));
}

TEST(StrideTest, sum_over_steps_equals_distance) {
    for (uint32_t distance : {1u, 7u, 100u, 999u, 1000000u}) {
        for (uint32_t steps : {1u, 2u, 3u, 7u, 13u, 100u}) {
            SCOPED_TRACE("distance=" + std::to_string(distance) + " steps=" + std::to_string(steps));
            EXPECT_EQ(sum(collect(distance, steps)), distance);
        }
    }
}
