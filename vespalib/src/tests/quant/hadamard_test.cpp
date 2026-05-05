// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/quant/hadamard.h>
#include <vespa/vespalib/util/fast_range.h>
#include <vespa/vespalib/util/xoshiro.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <gmock/gmock.h>
#include <cmath>
#include <vector>

using namespace ::testing;

namespace vespalib::quant {

MATCHER_P(IsSquareRootNormalizedBy, dimensions, "") {
    // Pointwise() matches on a 2-tuple <actual, expected>, i.e. lhs and rhs container args.
    const float actual   = std::get<0>(arg);
    const float expected = std::get<1>(arg) / std::sqrt(dimensions);
    return ExplainMatchResult(FloatEq(expected), actual, result_listener);
}

// To minimize floating point hassle, use int vectors as much as possible.
// However, the normalization step still requires floating point.
void check_hadamard(const std::vector<int>& in, const std::vector<int>& expected) {
    const size_t n = in.size();
    ASSERT_TRUE(n == 0 || std::has_single_bit(n));
    ASSERT_EQ(n, expected.size());

    std::vector v = in;
    // Non-normalized Hadamard transforms:
    std::vector<int> tmp(n);
    int* res = hadamard(v.data(), tmp.data(), n);
    ASSERT_THAT(std::span<const int>(res, n), ElementsAreArray(expected)) << "out-of-place FWHT";

    v = in;
    hadamard<int>(v.data(), v.size());
    ASSERT_THAT(v, ElementsAreArray(expected)) << "in-place FWHT";

    // Post-normalized Hadamard transforms:
    std::vector<float> v_f(in.begin(), in.end());
    hadamard_normalized<float>(v_f.data(), v_f.size());
    ASSERT_THAT(v_f, Pointwise(IsSquareRootNormalizedBy(n), expected)) << "normalized in-place FWHT";

    v_f.assign(in.begin(), in.end());
    std::vector<float> tmp_f(n);
    float* res_f = hadamard_normalized(v_f.data(), tmp_f.data(), v_f.size());
    ASSERT_THAT(std::span<const float>(res_f, n),
                Pointwise(IsSquareRootNormalizedBy(n), expected)) << "normalized out-of-place FWHT";
}

TEST(FastWHTransformTest, zero_and_one_point_transforms_are_no_ops) {
    // 0-point WHT
    ASSERT_NO_FATAL_FAILURE(check_hadamard({}, {}));
    // 1-point WHT
    ASSERT_NO_FATAL_FAILURE(check_hadamard({7}, {7}));
    ASSERT_NO_FATAL_FAILURE(check_hadamard({-21}, {-21}));
}

TEST(FastWHTransformTest, two_point_transforms_have_expected_output) {
    // Example calculations from the "The Walsh Hadamard Transform - Basics and Applications"
    ASSERT_NO_FATAL_FAILURE(check_hadamard({0, 0}, {0, 0}));
    ASSERT_NO_FATAL_FAILURE(check_hadamard({1, 0}, {1, 1}));
    ASSERT_NO_FATAL_FAILURE(check_hadamard({0, 1}, {1, -1}));
    ASSERT_NO_FATAL_FAILURE(check_hadamard({1, 1}, {2, 0}));
}

struct MyRandGen {
    Xoshiro256PlusPlusPrng rng;
    MyRandGen();
    // Returns random number in [-100, 100]
    [[nodiscard]] int operator()() noexcept {
        return 100 - static_cast<int>(next_random_in_range<uint8_t>(rng, 0, 201));
    }
};
// Avoid failed inlining warnings
MyRandGen::MyRandGen() : rng(std::random_device{}()) {}

// clang-format off: destroys semantic indenting

TEST(FastWHTransformTest, four_point_transforms_have_expected_output) {
    MyRandGen gen;
    for (size_t i = 0; i < 1'000; ++i) {
        const int a = gen(), b = gen(), c = gen(), d = gen();
        ASSERT_NO_FATAL_FAILURE(check_hadamard({a, b, c, d}, {a + b + c + d,
                                                              a - b + c - d,
                                                              a + b - c - d,
                                                              a - b - c + d}));
    }
}

TEST(FastWHTransformTest, wiki_example_has_expected_output) {
    // Example from https://en.wikipedia.org/wiki/Fast_Walsh%E2%80%93Hadamard_transform:
    // [1, 0, 1, 0, 0, 1, 1, 0] => [4, 2, 0, -2, 0, 2, 0, 2]
    ASSERT_NO_FATAL_FAILURE(check_hadamard({1, 0, 1,  0, 0, 1, 1, 0},
                                           {4, 2, 0, -2, 0, 2, 0, 2}));
}

TEST(FastWHTransformTest, eight_point_transforms_have_expected_output) {
    MyRandGen gen;
    for (size_t i = 0; i < 1'000; ++i) {
        const int a = gen(), b = gen(), c = gen(), d = gen(),
                  e = gen(), f = gen(), g = gen(), h = gen();
        ASSERT_NO_FATAL_FAILURE(check_hadamard({a, b, c, d, e, f, g, h},
                                               {a + b + c + d + e + f + g + h,
                                                a - b + c - d + e - f + g - h,
                                                a + b - c - d + e + f - g - h,
                                                a - b - c + d + e - f - g + h,
                                                a + b + c + d - e - f - g - h,
                                                a - b + c - d - e + f - g + h,
                                                a + b - c - d - e - f + g + h,
                                                a - b - c + d - e + f + g - h}));
    }
}

// ... from here we "inductively" assume that > 8 point transforms are also correct :D

TEST(FastWHTransformTest, can_explicitly_post_normalize_vector) {
    std::vector v = {3.f, 5.f, 7.f, 11.f};
    post_hadamard_normalize(v.data(), v.size());
    auto scaled = [s = 1.f / std::sqrt(v.size())](float f) noexcept { return f * s; };
    EXPECT_THAT(v, ElementsAre(FloatEq(scaled(3)), FloatEq(scaled(5)),
                               FloatEq(scaled(7)), FloatEq(scaled(11))));
}

TEST(FastWHTransformTest, normalized_hadamard_is_self_inverse) {
    const std::vector orig = {3.f, 5.f, 7.f, 11.f};
    std::vector v = orig;
    hadamard_normalized(v.data(), v.size());
    hadamard_normalized(v.data(), v.size());
    EXPECT_THAT(v, Pointwise(FloatEq(), orig));
}

// clang-format on

} // namespace vespalib::quant

GTEST_MAIN_RUN_ALL_TESTS()
