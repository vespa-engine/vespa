// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/quant/sign_flip.h>
#include <vespa/vespalib/util/xoshiro.h>

#include <gmock/gmock.h>

#include <algorithm>
#include <random>
#include <span>
#include <vector>

using namespace ::testing;

namespace vespalib::quant {

namespace {

template <std::floating_point T> void flip(std::span<T> f, const uint64_t seed) {
    Xoshiro256PlusPlusPrng prng(seed);
    flip_sign_bits(f, prng);
}

template <std::floating_point T> void do_test_full_block_sign_flips_are_deterministic_and_invertible() {
    SCOPED_TRACE("full block");
    std::vector<T> v(64); // Leaky abstraction alert: fixed block size of 64 due to 64 PRNG output bits.
    std::iota(v.begin(), v.end(), 1);
    const std::vector v_orig = v;

    flip<T>(v, 0xf00d);
    std::vector<T> expected = {1,   2,   -3,  4,   -5,  -6,  7,   -8,  9,   10, 11,  -12, -13, -14, -15, -16,
                               17,  -18, 19,  20,  -21, 22,  23,  24,  -25, 26, 27,  -28, 29,  30,  31,  32,
                               -33, 34,  -35, -36, -37, -38, -39, 40,  41,  42, -43, 44,  -45, -46, 47,  -48,
                               -49, -50, 51,  52,  53,  54,  -55, -56, 57,  58, -59, 60,  -61, 62,  -63, -64};
    EXPECT_THAT(v, ElementsAreArray(expected));

    flip<T>(v, 0xf00d);
    EXPECT_THAT(v, ElementsAreArray(v_orig));
}

template <std::floating_point T> void do_test_remainder_sign_flips_are_deterministic_and_invertible() {
    SCOPED_TRACE("block remainder");
    std::vector<T> v = {1, 2, 3, 4, 5, 6, 7, 8};
    std::vector<T> v2 = {9, 10, 11, 12, 13, 14, 15, 16};

    flip<T>(v, 0x1337);
    EXPECT_THAT(v, ElementsAre(1, -2, -3, 4, 5, -6, 7, 8));
    flip<T>(v2, 0xc0ffee);
    EXPECT_THAT(v2, ElementsAre(-9, -10, -11, 12, -13, -14, 15, 16));

    // Invert flipping by running with same PRNG state
    flip<T>(v, 0x1337);
    EXPECT_THAT(v, ElementsAre(1, 2, 3, 4, 5, 6, 7, 8));
    flip<T>(v2, 0xc0ffee);
    EXPECT_THAT(v2, ElementsAre(9, 10, 11, 12, 13, 14, 15, 16));
}

} // namespace

TEST(SignFlipTest, float_sign_flips_are_deterministic_and_invertible) {
    do_test_full_block_sign_flips_are_deterministic_and_invertible<float>();
    do_test_remainder_sign_flips_are_deterministic_and_invertible<float>();
}

TEST(SignFlipTest, double_sign_flips_are_deterministic_and_invertible) {
    do_test_full_block_sign_flips_are_deterministic_and_invertible<double>();
    do_test_remainder_sign_flips_are_deterministic_and_invertible<double>();
}

TEST(SignFlipTest, sign_flipping_buffer_boundary_cases) {
    Xoshiro256PlusPlusPrng prng(std::random_device{}());

    constexpr size_t max_dims = 1024;
    for (size_t d = 0; d < max_dims; ++d) {
        std::vector<float>           v(d);
        const Xoshiro256PlusPlusPrng flip_seed_prng(prng());
        Xoshiro256PlusPlusPrng       value_prng(prng());

        for (size_t i = 0; i < d; ++i) {
            // We do not care about silly things such as creating NaNs and subnormals etc.,
            // just about the raw bit patterns.
            v[i] = std::bit_cast<float>(static_cast<uint32_t>(value_prng()));
        }
        const std::vector v_check = v;

        Xoshiro256PlusPlusPrng flip_prng(flip_seed_prng);
        flip_sign_bits<float>(v, flip_prng);

        Xoshiro256PlusPlusPrng inv_flip_prng(flip_seed_prng);
        flip_sign_bits<float>(v, inv_flip_prng);
        // Check exact before/after bit patterns. Don't defer to FP equality rules, as we
        // (as mentioned above) may create all kinds of exciting special-case FP values.
        for (size_t i = 0; i < d; ++i) {
            ASSERT_EQ(std::bit_cast<uint32_t>(v[i]), std::bit_cast<uint32_t>(v_check[i]));
        }
    }
}

} // namespace vespalib::quant
