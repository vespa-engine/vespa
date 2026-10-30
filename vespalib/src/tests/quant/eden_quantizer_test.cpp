// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/check_require.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/quant/eden.h>
#include <vespa/vespalib/util/xoshiro.h>

#include <gmock/gmock.h>

#include <cmath>
#include <random>
#include <ranges>
#include <span>
#include <vector>

using namespace ::testing;

namespace vespalib::quant {

namespace {

// Mean Squared Error (MSE)
[[nodiscard]] float mse(std::span<const float> expected, std::span<const float> actual) noexcept {
    CHECK(expected.size() == actual.size());
    float sum_err_sq = 0;
    for (size_t i = 0; i < expected.size(); ++i) {
        const float diff = expected[i] - actual[i];
        sum_err_sq += diff * diff;
    }
    return sum_err_sq / static_cast<float>(expected.size());
}

// Your friendly neighborhood Euclidean vector length
[[nodiscard]] float l2_norm(std::span<const float> v) noexcept {
    float l2 = 0;
    for (float c : v) {
        l2 += c * c;
    }
    return std::sqrtf(l2);
}

// Simple non-vectorized dot product (sanity check vs. vectorized functions)
[[nodiscard]] float dot(std::span<const float> a, std::span<const float> b) noexcept {
    CHECK(a.size() == b.size());
    float d = 0;
    for (size_t i = 0; i < a.size(); ++i) {
        d += a[i] * b[i];
    }
    return d;
}

// Simple non-vectorized squared Euclidean distance
[[nodiscard]] float squared_euclidean_distance(std::span<const float> a, std::span<const float> b) noexcept {
    CHECK(a.size() == b.size());
    float d = 0;
    for (size_t i = 0; i < a.size(); ++i) {
        const float delta = a[i] - b[i];
        d += delta * delta;
    }
    return d;
}

void normalize(std::span<float> v) noexcept {
    const float norm_recip = 1.f / l2_norm(v);
    for (auto& c : v) {
        c *= norm_recip;
    }
}

[[nodiscard]] float extract_scale(std::span<const uint8_t> v) noexcept {
    CHECK(v.size() >= 4);
    float scale;
    memcpy(&scale, v.data(), sizeof(float));
    return scale;
}

struct QuantTestParams {
    uint8_t              bits = 1;
    uint64_t             seed = 0;
    QuantMode            quant_mode = QuantMode::MSE;
    std::vector<float>   in_v;
    float                expected_scale = 0;
    float                expected_mse = 0;
    std::vector<uint8_t> expected_quant_bytes;
    std::vector<float>   expected_dequant;

    QuantTestParams();
    ~QuantTestParams();
};

QuantTestParams::QuantTestParams() = default;
QuantTestParams::~QuantTestParams() = default;

void do_test_quantization_roundtrip(const QuantTestParams& qtp) {
    EdenQuantizer        q(qtp.in_v.size(), qtp.bits, qtp.seed);
    std::vector<uint8_t> v_q(q.quantized_size());
    q.quantize(qtp.in_v, v_q, qtp.quant_mode);
    // We extract the scale and compare it as a float to avoid precision issues
    float scale = extract_scale(v_q);
    EXPECT_THAT(scale, FloatEq(qtp.expected_scale));
    EXPECT_THAT(std::span(v_q).subspan(4), ElementsAreArray(qtp.expected_quant_bytes));

    std::vector<float> v_dq(qtp.in_v.size());
    q.dequantize(v_q, v_dq);
    EXPECT_THAT(v_dq, Pointwise(FloatEq(), qtp.expected_dequant));
    EXPECT_THAT(mse(qtp.in_v, v_dq), FloatEq(qtp.expected_mse));
}

[[nodiscard]] std::vector<float> my_16d_test_vector() {
    // Test numbers expertly chosen by human poking at a keyboard(tm)
    return {3, -4, 5, -5, -4, 3, 2, -3, 4, -5, 4, -3, -3, 4, 5, -4};
}

} // namespace

TEST(EdenQuantizerTest, quantizer_properties_have_expected_values) {
    {
        EdenQuantizer q(16, 1, 0x12345678);
        EXPECT_EQ(q.dimensions(), 16);
        EXPECT_EQ(q.bits(), 1);
        EXPECT_EQ(q.seed(), 0x12345678);
        EXPECT_EQ(q.quantized_size(), 4 + 2);
    }
    {
        EdenQuantizer q(21, 4, 0xfeedbaaaaaadf00dULL);
        EXPECT_EQ(q.dimensions(), 21);
        EXPECT_EQ(q.bits(), 4);
        EXPECT_EQ(q.seed(), 0xfeedbaaaaaadf00dULL);
        EXPECT_EQ(q.quantized_size(), 4 + 11); // last byte will only use 1 nibble
    }
}

TEST(EdenQuantizerTest, quantization_is_deterministic_for_a_given_seed_1_bit) {
    QuantTestParams qtp;
    qtp.bits = 1;
    qtp.in_v = my_16d_test_vector();
    qtp.seed = 0x1337beef;
    qtp.quant_mode = QuantMode::MSE;
    qtp.expected_scale = 3.80392146;
    qtp.expected_mse = 6.12402296;
    qtp.expected_quant_bytes = std::vector<uint8_t>{0xDA, 0x41};
    // "We have vector at home":
    qtp.expected_dequant =
        std::vector<float>{1.515625, -7.578125, 1.515625, -1.515625, -1.515625, 1.515625, 1.515625, -4.546875,
                           0,        -0,        3.03125,  -3.03125,  -3.03125,  3.03125,  3.03125,  -3.03125};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));

    qtp.quant_mode = QuantMode::InnerProduct;
    // Quantization bits shall be the same as for MSE; only the scaling factor differs.
    qtp.expected_scale = 6.33919525;
    qtp.expected_mse = 10.2056208;
    qtp.expected_dequant = std::vector<float>{
        2.52577305, -12.6288652, 2.52577305, -2.52577305, -2.52577305, 2.52577305, 2.52577305, -7.57731915,
        0,          -0,          5.0515461,  -5.0515461,  -5.0515461,  5.0515461,  5.0515461,  -5.0515461};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));

    // Different seed results in different rotations. We only test multiple seeds for 1 bit
    // and make the simplifying assumption that this also holds for the other bit widths
    // since this is technically a property of the Rotator component, which has its own suite
    // of tests.
    qtp.seed = 0xdeadbeef;
    qtp.quant_mode = QuantMode::MSE;
    qtp.expected_scale = 3.84313726;
    qtp.expected_mse = 5.93359375;
    qtp.expected_quant_bytes = std::vector<uint8_t>{0x4C, 0xD6};
    qtp.expected_dequant = std::vector<float>{3.0625, -3.0625, 3.0625, -3.0625, -6.125, 0,     -0, 0,
                                              3.0625, -3.0625, 3.0625, -3.0625, -0,     6.125, -0, -0};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));
}

TEST(EdenQuantizerTest, quantization_is_deterministic_for_a_given_seed_2_bits) {
    QuantTestParams qtp;
    qtp.bits = 2;
    qtp.in_v = my_16d_test_vector();
    qtp.seed = 0x1337beef;
    qtp.quant_mode = QuantMode::MSE;
    qtp.expected_scale = 4.76700592;
    qtp.expected_mse = 1.86718082;
    qtp.expected_quant_bytes = std::vector<uint8_t>{0xA4, 0x82, 0xCA, 0x01};
    qtp.expected_dequant = std::vector<float>{
        1.07536983, -5.37684774, 3.59387541, -3.59387565, -6.11238098, 1.07536983, 1.0753696,  -3.22610784,
        1.25925303, -3.77775908, 3.40999222, -3.40999246, -3.40999198, 3.40999222, 5.92849827, -3.40999198};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));
    // InnerProduct only differs in scale, so we assume testing that for 1 bit is sufficient.
}

TEST(EdenQuantizerTest, quantization_is_deterministic_for_a_given_seed_3_bits) {
    QuantTestParams qtp;
    qtp.bits = 3;
    qtp.in_v = my_16d_test_vector();
    qtp.seed = 0x1337beef;
    qtp.quant_mode = QuantMode::MSE;
    qtp.expected_scale = 3.93120503;
    qtp.expected_mse = 0.52801168;
    qtp.expected_quant_bytes = std::vector<uint8_t>{0xE9, 0x0D, 0x44, 0x83, 0x2B, 0x28};
    qtp.expected_dequant = std::vector<float>{
        1.48188,    -3.40141392, 5.20961428, -5.2096138,  -4.36501932, 2.04622269, 1.48187971, -4.44564,
        3.14803529, -4.30743361, 3.54345942, -3.54345894, -3.54345894, 3.54345942, 5.2672,     -4.10780191};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));
}

TEST(EdenQuantizerTest, quantization_is_deterministic_for_a_given_seed_4_bits) {
    QuantTestParams qtp;
    qtp.bits = 4;
    qtp.in_v = my_16d_test_vector();
    qtp.seed = 0x1337beef;
    qtp.quant_mode = QuantMode::MSE;
    qtp.expected_scale = 3.71809769;
    qtp.expected_mse = 0.0785451531;
    qtp.expected_quant_bytes = std::vector<uint8_t>{0x62, 0x1B, 0x89, 0x17, 0xAA, 0xA2, 0xC0, 0x30};
    qtp.expected_dequant = std::vector<float>{
        2.81671309, -3.76802373, 5.45097113, -5.45097065, -4.0112977, 2.86391544, 1.80549192, -3.07451057,
        3.51839471, -5.15232468, 3.78708601, -2.72684669, -2.7758646, 3.73806763, 5.37018156, -3.73625231};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));
}

TEST(EdenQuantizerTest, zero_norm_vector_emits_zero_scale_and_quantization_indexes) {
    std::vector<float>   v(16, 0.0f);
    EdenQuantizer        q(16, 4, 0xbeefd00d);
    std::vector<uint8_t> v_q(q.quantized_size());

    q.quantize(v, v_q, QuantMode::MSE);
    float scale = extract_scale(v_q);
    EXPECT_EQ(scale, 0.0f); // Bitwise exact
    // With a scale of zero, the actual centroid indexes don't matter. But for
    // consistency these will also always be zero when the norm is zero.
    EXPECT_THAT(std::span(v_q).subspan(4), Each(Eq(0x00)));
    // Similarly, dequantization should yield all zeros
    std::vector<float> v_dq(v.size());
    q.dequantize(v_q, v_dq);
    EXPECT_THAT(v_dq, Each(Eq(0.0f)));
}

namespace {

template <std::uniform_random_bit_generator Rng>
void fill_random(std::span<float> v, Rng& rng) noexcept {
    std::uniform_real_distribution dist(-1.f, 1.f);
    std::ranges::generate(v, [&] { return dist(rng); });
}

void do_test_mixed_dot_product_preserves_magnitude_and_relative_angles(const uint8_t bits) {
    SCOPED_TRACE("do_test_mixed_dot_product_preserves_magnitude_and_relative_angles");
    EdenQuantizer        q(16, bits, 0xbeefd00d);
    std::vector<uint8_t> v_q(q.quantized_size());

    std::vector v = my_16d_test_vector();
    // Normalize v so that <v, v> is 1
    normalize(v);
    ASSERT_THAT(l2_norm(v), FloatEq(1));
    q.quantize(v, v_q, QuantMode::InnerProduct);
    // First let's do a silly dot product between a _non-rotated_ query and its quantized
    // version; the result should be completely uncorrelated.
    EXPECT_THAT(q.pre_rotated_query_dot_product(v, v_q), Lt(0));
    // Now let's do it properly
    std::vector q_rot = v;
    q.rotate_vector_inplace(q_rot);
    // Vector length shall be preserved
    EXPECT_THAT(l2_norm(q_rot), FloatEq(1));
    // <v, v> == 1, so <v, v_q> should be ~= 1
    EXPECT_THAT(q.pre_rotated_query_dot_product(q_rot, v_q), FloatEq(1));
    // Point the query vector the other way
    for (float& c : q_rot) {
        c = -c;
    }
    EXPECT_THAT(q.pre_rotated_query_dot_product(q_rot, v_q), FloatEq(-1));
}

void do_test_mixed_dot_product_equal_to_explicitly_dequantized_dot(const size_t dimensions, const uint8_t bits) {
    SCOPED_TRACE("do_test_mixed_dot_product_equal_to_explicitly_dequantized_dot");
    Xoshiro256PlusPlusPrng prng(0xabcdef123456789);
    EdenQuantizer          q(dimensions, bits, prng());

    std::vector<float>   lhs(dimensions);
    std::vector<float>   rhs(dimensions), rhs_dq(dimensions);
    std::vector<uint8_t> rhs_q(q.quantized_size());

    const size_t rounds = 100;
    for (size_t i = 0; i < rounds; ++i) {
        fill_random(lhs, prng);
        fill_random(rhs, prng);

        q.quantize(rhs, rhs_q, QuantMode::InnerProduct);
        q.dequantize(rhs_q, rhs_dq);

        const float real_f32_q_dot = dot(lhs, rhs_dq);
        std::vector q_rot = lhs;
        q.rotate_vector_inplace(q_rot);
        const float q_dot = q.pre_rotated_query_dot_product(q_rot, rhs_q);
        ASSERT_THAT(q_dot, FloatNear(real_f32_q_dot, 0.00005)) << "with dims=" << dimensions;
    }
}

void do_run_mixed_dot_product_tests(const uint8_t bits) {
    ASSERT_NO_FATAL_FAILURE(do_test_mixed_dot_product_preserves_magnitude_and_relative_angles(1));
    for (size_t dims : {1, 2, 3, 15, 16, 17, 64, 127, 128, 129, 1024}) {
        ASSERT_NO_FATAL_FAILURE(do_test_mixed_dot_product_equal_to_explicitly_dequantized_dot(dims, bits));
    }
}

} // namespace

TEST(EdenQuantizerTest, can_compute_dot_product_with_pre_rotated_vector_1_bit) {
    do_run_mixed_dot_product_tests(1);
}

TEST(EdenQuantizerTest, can_compute_dot_product_with_pre_rotated_vector_2_bits) {
    do_run_mixed_dot_product_tests(2);
}

TEST(EdenQuantizerTest, can_compute_dot_product_with_pre_rotated_vector_3_bits) {
    do_run_mixed_dot_product_tests(3);
}

TEST(EdenQuantizerTest, can_compute_dot_product_with_pre_rotated_vector_4_bits) {
    do_run_mixed_dot_product_tests(4);
}

namespace {

void do_test_quantized_vectors_dot_product(const size_t dimensions, const uint8_t bits) {
    Xoshiro256PlusPlusPrng prng(0x123456789abcdef); // Fixed seed due to floating point testing
    EdenQuantizer          q(dimensions, bits, prng());

    std::vector<float>   lhs(dimensions), lhs_dq(dimensions);
    std::vector<float>   rhs(dimensions), rhs_dq(dimensions);
    std::vector<uint8_t> lhs_q(q.quantized_size());
    std::vector<uint8_t> rhs_q(q.quantized_size());

    const size_t rounds = 100;
    for (size_t i = 0; i < rounds; ++i) {
        fill_random(lhs, prng);
        fill_random(rhs, prng);

        q.quantize(lhs, lhs_q, QuantMode::InnerProduct);
        q.quantize(rhs, rhs_q, QuantMode::InnerProduct);
        q.dequantize(lhs_q, lhs_dq);
        q.dequantize(rhs_q, rhs_dq);

        // The dot products between the _dequantized_ representations of the vectors and the dot
        // products between the _quantized_ representations should be very close. There is some
        // added rounding error due to dequantization requiring a rotation step, as well as any
        // differences in dot product vectorization. This error is independent of the bit width
        // used since rotation/dot product happens on values from the centroid codebook, and
        // these have the same, higher floating point precision.
        const float real_q_dot = dot(lhs_dq, rhs_dq);
        const float q_dot = q.quantized_lhs_rhs_dot_product(lhs_q, rhs_q);
        ASSERT_THAT(q_dot, FloatNear(real_q_dot, 0.00001));
    }
}

void do_test_quantized_vectors_dot_product(const uint8_t bits) {
    for (size_t dims : {1, 2, 3, 15, 16, 17, 64, 127, 128, 129}) {
        ASSERT_NO_FATAL_FAILURE(do_test_quantized_vectors_dot_product(dims, bits));
    }
}

} // namespace

TEST(EdenQuantizerTest, can_compute_dot_product_between_quantized_vectors_1_bit) {
    do_test_quantized_vectors_dot_product(1);
}

TEST(EdenQuantizerTest, can_compute_dot_product_between_quantized_vectors_2_bits) {
    do_test_quantized_vectors_dot_product(2);
}

TEST(EdenQuantizerTest, can_compute_dot_product_between_quantized_vectors_3_bits) {
    do_test_quantized_vectors_dot_product(3);
}

TEST(EdenQuantizerTest, can_compute_dot_product_between_quantized_vectors_4_bits) {
    do_test_quantized_vectors_dot_product(4);
}

TEST(EdenQuantizerTest, can_compute_squared_euclidean_distance_with_pre_rotated_vector) {
    EdenQuantizer        q(16, 4, 0xbeefd00d);
    std::vector<uint8_t> v_q(q.quantized_size());

    const std::vector v = my_16d_test_vector();
    q.quantize(v, v_q, QuantMode::MSE);

    const std::vector<float> query = {2, 2, 3, -5, 4, 1, 3, -4, 2, -1, 3, 2, -5, 1, 3, 2};

    std::vector query_rot = query;
    q.rotate_vector_inplace(query_rot);

    const float real_dist = squared_euclidean_distance(query, v);
    const float quant_dist = q.pre_rotated_query_squared_euclidean_distance(query_rot, v_q);
    EXPECT_THAT(quant_dist / real_dist, FloatNear(1.0, 0.021)); // ~2% error
}

namespace {

void do_test_quantized_vectors_squared_euclidean_distance(const size_t dimensions, const uint8_t bits) {
    Xoshiro256PlusPlusPrng prng(0x123456789abcdef); // Fixed seed due to floating point testing
    EdenQuantizer          q(dimensions, bits, prng());

    std::vector<float>   lhs(dimensions), lhs_dq(dimensions);
    std::vector<float>   rhs(dimensions), rhs_dq(dimensions);
    std::vector<uint8_t> lhs_q(q.quantized_size());
    std::vector<uint8_t> rhs_q(q.quantized_size());

    const size_t rounds = 100;
    for (size_t i = 0; i < rounds; ++i) {
        fill_random(lhs, prng);
        fill_random(rhs, prng);

        q.quantize(lhs, lhs_q, QuantMode::MSE);
        q.quantize(rhs, rhs_q, QuantMode::MSE);
        q.dequantize(lhs_q, lhs_dq);
        q.dequantize(rhs_q, rhs_dq);

        const float real_q_eqd = squared_euclidean_distance(lhs_dq, rhs_dq);
        ASSERT_NE(real_q_eqd, 0.0); // it could theoretically happen...
        const float q_eqd = q.quantized_lhs_rhs_squared_euclidean_distance(lhs_q, rhs_q);
        // Normalize for magnitude by computing relative error
        ASSERT_THAT(q_eqd / real_q_eqd, FloatNear(1.0, 0.0006f));
    }
}

void do_test_quantized_vectors_squared_euclidean_distance(const uint8_t bits) {
    for (size_t dims : {1, 2, 3, 15, 16, 17, 64, 127, 128, 129, 1024}) {
        ASSERT_NO_FATAL_FAILURE(do_test_quantized_vectors_squared_euclidean_distance(dims, bits));
    }
}

} // namespace

TEST(EdenQuantizerTest, can_compute_squared_euclidean_distance_between_quantized_vectors_1_bit) {
    do_test_quantized_vectors_squared_euclidean_distance(1);
}

TEST(EdenQuantizerTest, can_compute_squared_euclidean_distance_between_quantized_vectors_2_bits) {
    do_test_quantized_vectors_squared_euclidean_distance(2);
}

TEST(EdenQuantizerTest, can_compute_squared_euclidean_distance_between_quantized_vectors_3_bits) {
    do_test_quantized_vectors_squared_euclidean_distance(3);
}

TEST(EdenQuantizerTest, can_compute_squared_euclidean_distance_between_quantized_vectors_4_bits) {
    do_test_quantized_vectors_squared_euclidean_distance(4);
}

} // namespace vespalib::quant
