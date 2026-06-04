// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/quant/eden.h>

#include <gmock/gmock.h>

#include <cmath>
#include <ranges>
#include <span>
#include <vector>

using namespace ::testing;

namespace vespalib::quant {

namespace {

// Mean Squared Error (MSE)
[[nodiscard]] float mse(std::span<const float> expected, std::span<const float> actual) noexcept {
    assert(expected.size() == actual.size());
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

void normalize(std::span<float> v) noexcept {
    const float norm_recip = 1.f / l2_norm(v);
    for (auto& c : v) {
        c *= norm_recip;
    }
}

[[nodiscard]] float extract_scale(std::span<const uint8_t> v) noexcept {
    assert(v.size() >= 4);
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
    qtp.expected_scale = 3.79910803;
    qtp.expected_mse = 6.12402296;
    qtp.expected_quant_bytes = std::vector<uint8_t>{0x46, 0x71};
    // "We have vector at home":
    qtp.expected_dequant = std::vector<float>{
        1.51562476,     -7.57812405,     1.51562476, -1.51562476, -1.51562476, 1.51562476, 1.51562476, -4.54687452,
        1.78813934e-07, -2.38418579e-07, 3.03124976, -3.03124976, -3.03124976, 3.03124976, 3.03124976, -3.03124976};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));

    qtp.quant_mode = QuantMode::InnerProduct;
    // Quantization bits shall be the same as for MSE; only the scaling factor differs.
    qtp.expected_scale = 6.33117437;
    qtp.expected_mse = 10.2056208;
    qtp.expected_dequant = std::vector<float>{
        2.52577353,      -12.6288662,    2.52577353, -2.52577353, -2.52577353, 2.52577353, 2.52577353, -7.57731962,
        -3.57627869e-07, 4.76837158e-07, 5.05154657, -5.05154657, -5.05154657, 5.05154657, 5.05154657, -5.051546576};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));

    // Different seed results in different rotations. We only test multiple seeds for 1 bit
    // and make the simplifying assumption that this also holds for the other bit widths
    // since this is technically a property of the Rotator component, which has its own suite
    // of tests.
    qtp.seed = 0xdeadbeef;
    qtp.quant_mode = QuantMode::MSE;
    qtp.expected_scale = 3.83827376;
    qtp.expected_mse = 5.93359375;
    qtp.expected_quant_bytes = std::vector<uint8_t>{0x78, 0xB2};
    qtp.expected_dequant = std::vector<float>{
        3.06249952, -3.06249952, 3.06249952, -3.06249952, -6.12499905, 0,          1.1920929e-07, -1.1920929e-07,
        3.06249952, -3.06249952, 3.06249952, -3.06249952, -0,          6.12499905, 1.1920929e-07, -1.1920929e-07};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));
}

TEST(EdenQuantizerTest, quantization_is_deterministic_for_a_given_seed_2_bits) {
    QuantTestParams qtp;
    qtp.bits = 2;
    qtp.in_v = my_16d_test_vector();
    qtp.seed = 0x1337beef;
    qtp.quant_mode = QuantMode::MSE;
    qtp.expected_scale = 4.7562151;
    qtp.expected_mse = 1.86790919;
    qtp.expected_quant_bytes = std::vector<uint8_t>{0x29, 0x64, 0x56, 0x7A};
    qtp.expected_dequant = std::vector<float>{
        1.0767597,  -5.38379765, 3.5919354,  -3.59193587, -6.10711145, 1.07675958, 1.0767591,  -3.23027873,
        1.25758827, -3.77276397, 3.41110682, -3.4111073,  -3.41110754, 3.41110682, 5.92628288, -3.4111073};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));
    // InnerProduct only differs in scale, so we assume testing that for 1 bit is sufficient.
}

TEST(EdenQuantizerTest, quantization_is_deterministic_for_a_given_seed_3_bits) {
    QuantTestParams qtp;
    qtp.bits = 3;
    qtp.in_v = my_16d_test_vector();
    qtp.seed = 0x1337beef;
    qtp.quant_mode = QuantMode::MSE;
    qtp.expected_scale = 3.91597676;
    qtp.expected_mse = 0.524829865;
    qtp.expected_quant_bytes = std::vector<uint8_t>{0x2A, 0x81, 0x71, 0xD5, 0xD6, 0x5A};
    qtp.expected_dequant = std::vector<float>{
        1.48024964, -3.39981604, 5.21384239, -5.21384239, -4.36423588, 2.06201744, 1.4802494,  -4.44074869,
        3.1580379,  -4.30914736, 3.53605366, -3.53605366, -3.53605318, 3.53605366, 5.26893044, -4.11782122};
    ASSERT_NO_FATAL_FAILURE(do_test_quantization_roundtrip(qtp));
}

TEST(EdenQuantizerTest, quantization_is_deterministic_for_a_given_seed_4_bits) {
    QuantTestParams qtp;
    qtp.bits = 4;
    qtp.in_v = my_16d_test_vector();
    qtp.seed = 0x1337beef;
    qtp.quant_mode = QuantMode::MSE;
    qtp.expected_scale = 3.70460153;
    qtp.expected_mse = 0.0788929164;
    qtp.expected_quant_bytes = std::vector<uint8_t>{0xB5, 0x09, 0x61, 0x68, 0x5A, 0x77, 0xAA, 0x4C};
    qtp.expected_dequant = std::vector<float>{
        2.81700802, -3.76831293, 5.44971228, -5.4497118,  -4.02572727, 2.86288738, 1.80706978, -3.05899167,
        3.52310324, -5.16248846, 3.78170633, -2.72374034, -2.77176738, 3.73367882, 5.37091684, -3.73153186};
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

TEST(EdenQuantizerTest, can_compute_dot_product_with_pre_rotated_vector) {
    EdenQuantizer        q(16, 4, 0xbeefd00d);
    std::vector<uint8_t> v_q(q.quantized_size());

    std::vector v = my_16d_test_vector();
    // Normalize v so that <v, v> is 1
    normalize(v);
    ASSERT_THAT(l2_norm(v), FloatEq(1));
    q.quantize(v, v_q, QuantMode::InnerProduct);
    // First let's do a silly dot product between a _non-rotated_ query and its quantized
    // version; the result should be completely uncorrelated.
    EXPECT_THAT(q.pre_rotated_query_dot_product(v, v_q), FloatEq(-0.30234611));
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

} // namespace vespalib::quant
