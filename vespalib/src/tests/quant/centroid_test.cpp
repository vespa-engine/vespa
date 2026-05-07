// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/quant/centroid.h>

#include <array>

using namespace ::testing;

namespace vespalib::quant {

// This use-case doesn't make sense at all, but let's ensure that the code generalizes.
TEST(CentroidTest, single_centroid_choice_is_obvious) {
    constexpr std::array<int, 1> my_silly_centroid = {5};

    auto idx_of = [&](int v) noexcept { return closest_centroid_index<int>(v, my_silly_centroid); };
    EXPECT_EQ(idx_of(-100000), 0);
    EXPECT_EQ(idx_of(5), 0);
    EXPECT_EQ(idx_of(100000), 0);
}

TEST(CentroidTest, choose_closest_of_two_centroids) {
    constexpr std::array<int, 2> my_centroids = {-10, 10};

    auto idx_of = [&](int v) noexcept { return closest_centroid_index<int>(v, my_centroids); };
    EXPECT_EQ(idx_of(-100000), 0);
    EXPECT_EQ(idx_of(-10), 0);
    EXPECT_EQ(idx_of(-5), 0);
    EXPECT_EQ(idx_of(-1), 0);
    EXPECT_EQ(idx_of(0), 0); // Exact boundary "rounds down" since distance to the next centroid is not less
    EXPECT_EQ(idx_of(1), 1);
    EXPECT_EQ(idx_of(5), 1);
    EXPECT_EQ(idx_of(10), 1);
    EXPECT_EQ(idx_of(100000), 1);
}

TEST(CentroidTest, centroid_distance_can_be_non_uniform) {
    constexpr std::array<float, 4> my_centroids = {1, 2, 8, 20};

    auto idx_of = [&](float v) noexcept { return closest_centroid_index<float>(v, my_centroids); };
    EXPECT_EQ(idx_of(1), 0);
    EXPECT_EQ(idx_of(1.5), 0);
    EXPECT_EQ(idx_of(1.51), 1);
    EXPECT_EQ(idx_of(2), 1);
    EXPECT_EQ(idx_of(5), 1);
    EXPECT_EQ(idx_of(5.0001), 2);
    EXPECT_EQ(idx_of(14), 2);
    EXPECT_EQ(idx_of(14.0001), 3);
}

} // namespace vespalib::quant
