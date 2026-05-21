// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/quant/rotator.h>

#include <gmock/gmock.h>

#include <algorithm>
#include <random>
#include <span>
#include <vector>

using namespace ::testing;

namespace vespalib::quant {

TEST(RotatorTest, power_of_two_rotations_are_deterministic_and_invertible) {
    Rotator            rot(16, 0xc0ffeed00d);
    std::vector<float> v = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
    const std::vector  v_orig = v;

    rot.rotate_forward(v);
    // 16 is a nice and round input size since the FWHT normalization factor becomes
    // 1/sqrt(16), i.e. 1/4. This avoids most floating point precision issues since
    // it's a "clean" power-of-2 exponent.
    const std::vector<float> expected = {-9, 2, 5, -6, -17, 2, -9, 2, -7, -20, 3, 16, 11, 8, 3, -8};
    EXPECT_THAT(v, ElementsAreArray(expected));

    rot.rotate_inverse(v);
    EXPECT_THAT(v, ElementsAreArray(v_orig));

    // Different seed should give different rotation
    Rotator rot2(16, 0xca7f00d);
    v = v_orig;
    rot2.rotate_forward(v);
    const std::vector<float> expected2 = {-8.75, 3.25, -0.25, 7.75, -7.25, 12.75, -20.75, 17.25,
                                          9.75,  8.75, 5.25,  0.25, -1.75, 7.25,  -10.25, 6.75};
    EXPECT_THAT(v, ElementsAreArray(expected2));

    rot2.rotate_inverse(v);
    EXPECT_THAT(v, ElementsAreArray(v_orig));
}

TEST(RotatorTest, non_power_of_two_rotations_are_deterministic_and_invertible_hi_lo_overlap) {
    Rotator rot(9, 0xc0ffeed00d);
    // 1 greater than a power of 2 size has _maximal_ overlap (8 lo, 8 hi, 7 overlapping),
    // so only high/low FWHT is used
    std::vector<float> v = {1, 2, 3, 4, 5, 6, 7, 8, 9};
    const std::vector  v_orig = v;

    rot.rotate_forward(v);
    const std::vector<float> expected = {2.3169422, 4.0296335, 1.247017, 2.5236673, 6.7529817,
                                         12.110977, 1.1656718, 3.889022, 6.834327};
    EXPECT_THAT(v, Pointwise(FloatEq(), expected));

    rot.rotate_inverse(v);
    // Though rotations are fully invertible, in the common case this is a lossy
    // operation due to FP precision.
    EXPECT_THAT(v, Pointwise(FloatNear(0.00001), v_orig));
}

TEST(RotatorTest, non_power_of_two_rotations_are_deterministic_and_invertible_hi_lo_mid_overlap) {
    Rotator rot(7, 0xc0ffeed00d);
    // 1 less than power a of 2 size has _minimal_ overlap (4 lo, 4 hi, 1 overlapping),
    // so full high/low/mid FWHT is used
    std::vector<float> v = {1, 2, 3, 4, 5, 6, 7};
    const std::vector  v_orig = v;

    rot.rotate_forward(v);
    const std::vector<float> expected = {-4, 9.75, 2, -0.25, 4.5, -1.75, -1.25};
    EXPECT_THAT(v, Pointwise(FloatEq(), expected));

    rot.rotate_inverse(v);
    EXPECT_THAT(v, Pointwise(FloatNear(0.000001), v_orig));
}

TEST(RotatorTest, rotations_are_invertible_across_dimension_range) {
    // Since this involves floating point comparisons, make the seed deterministic.
    Xoshiro256PlusPlusPrng prng(0x1234567890);
    for (size_t d = 1; d < 1025; ++d) {
        std::uniform_real_distribution<float> dist(-1.f, 1.f);
        std::vector<float>                    v(d);
        std::ranges::generate(v, [&]() mutable { return dist(prng); });
        const std::vector v_orig = v;

        Rotator rot(d, prng());
        rot.rotate_forward(v);
        rot.rotate_inverse(v);

        ASSERT_THAT(v, Pointwise(FloatNear(0.000001), v_orig)) << "dimensions=" << d;
    }
}

} // namespace vespalib::quant
