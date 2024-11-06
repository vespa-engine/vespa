// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/util/relative_frequency_sketch.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace vespalib {

using namespace ::testing;

namespace {

struct Identity {
    template <typename T>
    constexpr T operator()(T v) const noexcept { return v; }
};

}

struct RelativeFrequencySketchTest : Test {
    // Note: although the sketch is inherently _probabilistic_, the below tests are fully
    // deterministic as long as the underlying hash function remains the same. This is also why
    // we explicitly do _not_ use std::hash here, but defer entirely to (deterministic) XXH3.
    using U32FrequencySketch = RelativeFrequencySketch<uint32_t, Identity>;
};

TEST_F(RelativeFrequencySketchTest, frequency_estimates_are_initially_zero) {
    U32FrequencySketch sketch(2);
    EXPECT_EQ(sketch.count_min(0), 0);
    EXPECT_EQ(sketch.count_min(12345), 0);
    EXPECT_EQ(sketch.estimate_relative_frequency(123, 456), std::weak_ordering::equivalent);
}

TEST_F(RelativeFrequencySketchTest, frequency_is_counted_up_to_and_saturated_at_15) {
    U32FrequencySketch sketch(1);
    for (uint32_t i = 1; i <= 20; ++i) {
        sketch.add(7);
        // With only one entry we're guaranteed to be exact up to the saturation point
        if (i < 15) {
            EXPECT_EQ(sketch.count_min(7), i);
        } else {
            EXPECT_EQ(sketch.count_min(7), 15);
        }
    }
}

TEST_F(RelativeFrequencySketchTest, can_track_frequency_of_multiple_elements) {
    U32FrequencySketch sketch(3);
    sketch.add(100);
    sketch.add(200);
    sketch.add(300);
    sketch.add(200);

    EXPECT_EQ(sketch.count_min(100), 1);
    EXPECT_EQ(sketch.count_min(200), 2);
    EXPECT_EQ(sketch.count_min(300), 1);
    EXPECT_EQ(sketch.count_min(400), 0);

    EXPECT_EQ(sketch.estimate_relative_frequency(0, 100),   std::weak_ordering::less);
    EXPECT_EQ(sketch.estimate_relative_frequency(100, 0),   std::weak_ordering::greater);
    EXPECT_EQ(sketch.estimate_relative_frequency(100, 100), std::weak_ordering::equivalent);
    EXPECT_EQ(sketch.estimate_relative_frequency(100, 300), std::weak_ordering::equivalent);
    EXPECT_EQ(sketch.estimate_relative_frequency(300, 100), std::weak_ordering::equivalent);
    EXPECT_EQ(sketch.estimate_relative_frequency(100, 200), std::weak_ordering::less);
    EXPECT_EQ(sketch.estimate_relative_frequency(200, 100), std::weak_ordering::greater);
}

TEST_F(RelativeFrequencySketchTest, counters_are_divided_by_2_once_window_size_reached) {
    U32FrequencySketch sketch(8);
    const auto ws = sketch.window_size();
    std::vector<uint32_t> truth(8);
    ASSERT_GT(ws, 0);
    for (size_t i = 0; i < ws - 1; ++i) { // don't trigger decay just yet
        uint32_t elem = i % 8;
        sketch.add(elem);
        truth[elem]++;
    }
    std::vector<uint32_t> c_before(8);
    for (uint32_t i = 0; i < 8; ++i) {
        c_before[i] = sketch.count_min(i);
        EXPECT_GE(c_before[i], truth[i]);
        // No counters should be saturated yet
        EXPECT_LT(c_before[i], 15);
    }
    // Edge triggered sample ==> should divide all counters
    sketch.add(9);
    for (uint32_t i = 0; i < 8; ++i) {
        EXPECT_EQ(sketch.count_min(i), c_before[i] / 2);
    }
}

}
