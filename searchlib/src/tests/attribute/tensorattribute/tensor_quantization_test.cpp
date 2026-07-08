// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/value_type_spec.h>
#include <vespa/searchlib/tensor/tensor_quantization.h>
#include <vespa/vespalib/quant/eden.h>

#include <gmock/gmock.h>

#include <format>
#include <iostream>

using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using TensorValue = vespalib::eval::Value; // ambiguity with GTest testing::Value
using vespalib::BFloat16;
using vespalib::eval::Int8Float;
using vespalib::eval::ValueType;
using vespalib::quant::EdenQuantizer;
using vespalib::quant::QuantMode;

using namespace ::testing;

namespace search::tensor {

namespace {

TensorValue::UP create_tensor(const TensorSpec& spec) {
    return value_from_spec(spec, FastValueBuilderFactory::get());
}

std::unique_ptr<EdenQuantizer> make_quantizer_from_type(const ValueType& type, const uint8_t bits) {
    const size_t   dimensions = type.dense_subspace_size();
    const uint64_t seed = 0x1234567890;
    return std::make_unique<EdenQuantizer>(dimensions, bits, seed);
}

std::string to_quant_spec(const std::string& spec, uint8_t bits) {
    auto in_type = ValueType::from_spec(spec);
    auto q = make_quantizer_from_type(in_type, bits);
    return to_quantized_tensor_type(in_type, *q).to_spec();
}

} // namespace

TEST(TensorQuantizationTest, quantized_type_conversion_flattens_indexed_dimensions) {
    EXPECT_EQ(to_quant_spec("tensor<float>(x[16])", 1), "tensor<int8>(x[6])");
    EXPECT_EQ(to_quant_spec("tensor<float>(x[17])", 1), "tensor<int8>(x[7])");
    EXPECT_EQ(to_quant_spec("tensor<float>(x[16])", 4), "tensor<int8>(x[12])");
    // For multiple indexed dimensions we choose the name of the last dimension (in lexicographic order)
    EXPECT_EQ(to_quant_spec("tensor<float>(x[2],y[3])", 4), "tensor<int8>(y[7])");
    EXPECT_EQ(to_quant_spec("tensor<float>(x[2],y[3],z[4])", 4), "tensor<int8>(z[16])");

    EXPECT_EQ(to_quant_spec("tensor<float>(a{},x[16])", 1), "tensor<int8>(a{},x[6])");
    EXPECT_EQ(to_quant_spec("tensor<float>(a{},x[16])", 4), "tensor<int8>(a{},x[12])");
    EXPECT_EQ(to_quant_spec("tensor<float>(a{},b{},x[16])", 4), "tensor<int8>(a{},b{},x[12])");
    EXPECT_EQ(to_quant_spec("tensor<float>(a{},x[2],y[3],z[4])", 4), "tensor<int8>(a{},z[16])");
    EXPECT_EQ(to_quant_spec("tensor<float>(a{},b{},x[2],y[3],z[4])", 4), "tensor<int8>(a{},b{},z[16])");

    // Tensor dimensions are normalized to lexicographic order, so we must not be
    // ordering-dependent in our type conversion.
    EXPECT_EQ(to_quant_spec("tensor<float>(z[4],y[2],x[3])", 4), "tensor<int8>(z[16])");
    EXPECT_EQ(to_quant_spec("tensor<float>(y[4],z[2],x[3])", 4), "tensor<int8>(z[16])");
    EXPECT_EQ(to_quant_spec("tensor<float>(x{},a[16])", 4), "tensor<int8>(a[12],x{})");
    EXPECT_EQ(to_quant_spec("tensor<float>(a[16],x{})", 4), "tensor<int8>(a[12],x{})");
    EXPECT_EQ(to_quant_spec("tensor<float>(x{},y{},a[2],b[3],c[4])", 4), "tensor<int8>(c[16],x{},y{})");
}

TEST(TensorQuantizationTest, quantized_tensor_shape_is_invariant_of_input_cell_type) {
    EXPECT_EQ(to_quant_spec("tensor(x[16])", 1), "tensor<int8>(x[6])");
    EXPECT_EQ(to_quant_spec("tensor<double>(x[16])", 1), "tensor<int8>(x[6])"); // alias of the above
    EXPECT_EQ(to_quant_spec("tensor<float>(x[16])", 1), "tensor<int8>(x[6])");
    EXPECT_EQ(to_quant_spec("tensor<bfloat16>(x[16])", 1), "tensor<int8>(x[6])");
    EXPECT_EQ(to_quant_spec("tensor<int8>(x[16])", 1), "tensor<int8>(x[6])");

    EXPECT_EQ(to_quant_spec("tensor(a{},b{},x[2],y[3],z[4])", 4), "tensor<int8>(a{},b{},z[16])");
    EXPECT_EQ(to_quant_spec("tensor<double>(a{},b{},x[2],y[3],z[4])", 4), "tensor<int8>(a{},b{},z[16])");
    EXPECT_EQ(to_quant_spec("tensor<float>(a{},b{},x[2],y[3],z[4])", 4), "tensor<int8>(a{},b{},z[16])");
    EXPECT_EQ(to_quant_spec("tensor<bfloat16>(a{},b{},x[2],y[3],z[4])", 4), "tensor<int8>(a{},b{},z[16])");
    EXPECT_EQ(to_quant_spec("tensor<int8>(a{},b{},x[2],y[3],z[4])", 4), "tensor<int8>(a{},b{},z[16])");
}

namespace {

struct DequantizedTensors {
    std::unique_ptr<TensorValue> mse_dequant; // MSE-optimized quantization mode
    std::unique_ptr<TensorValue> ip_dequant;  // Inner-product-optimized quantization mode
};

DequantizedTensors quantize_tensor_roundtrip(const TensorValue& t, const uint8_t q_bits) {
    auto q = make_quantizer_from_type(t.type(), q_bits);
    auto q_type = to_quantized_tensor_type(t.type(), *q);

    std::vector<float> scratch_space;

    auto qt_mse = search::tensor::quantize_tensor(t, q_type, *q, QuantMode::MSE, scratch_space);
    auto dqt_mse = search::tensor::dequantize_tensor(*qt_mse, t.type(), *q, scratch_space);

    auto qt_ip = search::tensor::quantize_tensor(t, q_type, *q, QuantMode::InnerProduct, scratch_space);
    auto dqt_ip = search::tensor::dequantize_tensor(*qt_ip, t.type(), *q, scratch_space);

    EXPECT_EQ(qt_mse->type(), q_type);
    EXPECT_EQ(qt_ip->type(), q_type);

    return {std::move(dqt_mse), std::move(dqt_ip)};
}

DequantizedTensors quantize_indexed_tensor_roundtrip(const CellType cell_type, const uint8_t q_bits = 4) {
    std::string cell_type_name = vespalib::eval::value_type::cell_type_to_name(cell_type);
    std::string spec_str = std::format("tensor<{}>(x[2],y[3])", cell_type_name);
    // Values are losslessly representable across all cell types, and will therefore
    // result in the same float values used as input to the quantization algorithm.
    auto t = create_tensor(TensorSpec(spec_str).add({{"x", 0}, {"y", 1}}, 12).add({{"x", 1}, {"y", 2}}, 9));

    return quantize_tensor_roundtrip(*t, q_bits);
}

template <typename CT>
void do_test_4_bit_quantized_roundtrip_of_dense_indexed_floating_point_tensor() {
    auto dq = quantize_indexed_tensor_roundtrip(vespalib::eval::get_cell_type<CT>(), 4);

    // We can use the same expected floating point values for double/float/BFloat16, since doubles
    // are implicitly converted to floats (and thus have no more actual precision) and BFloat16 is
    // implicitly mantissa-truncated before comparison.
    std::vector<CT> mse_expected = {-0.36424255, 11.988836, 1.243578, -0.30136871, -0.40498352, 8.7953281};
    EXPECT_THAT(dq.mse_dequant->cells().template typify<CT>(), Pointwise(FloatNear(0.00001), mse_expected));

    std::vector<CT> ip_expected = {-0.36747026, 12.095058, 1.2545962, -0.30403852, -0.40857196, 8.8732548};
    EXPECT_THAT(dq.ip_dequant->cells().template typify<CT>(), Pointwise(FloatNear(0.00001), ip_expected));
}

DequantizedTensors quantize_mixed_tensor_roundtrip(const CellType cell_type, const uint8_t q_bits = 4) {
    std::string cell_type_name = vespalib::eval::value_type::cell_type_to_name(cell_type);
    std::string spec_str = std::format("tensor<{}>(a{{}},x[2],y[3])", cell_type_name);

    // Will be normalized and arranged as bar=[0, 5, 0, 0, -3, 0], foo=[0, 12, 0, 0, 0, 9] in memory.
    auto t = create_tensor(TensorSpec(spec_str)
                               .add({{"a", "foo"}, {"x", 0}, {"y", 1}}, 12)
                               .add({{"a", "foo"}, {"x", 1}, {"y", 2}}, 9)
                               .add({{"a", "bar"}, {"x", 0}, {"y", 1}}, 5)
                               .add({{"a", "bar"}, {"x", 1}, {"y", 1}}, -3));

    return quantize_tensor_roundtrip(*t, q_bits);
}

template <typename CT>
void do_test_4_bit_quantized_roundtrip_of_mixed_floating_point_tensor() {
    auto dq = quantize_mixed_tensor_roundtrip(vespalib::eval::get_cell_type<CT>(), 4);

    // As with indexed tensors, we can reuse the expected values across FP cell types.
    std::vector<CT> mse_expected = {-0.4283701, 4.92065,  0.228362, -0.08518672, -3.048389,  -0.005629063,
                                    -0.3642426, 11.98884, 1.243578, -0.3013687,  -0.4049835, 8.795328};
    EXPECT_THAT(dq.mse_dequant->cells().template typify<CT>(), Pointwise(DoubleNear(0.00001), mse_expected));

    std::vector<CT> ip_expected = {-0.4315633, 4.957332, 0.2300643, -0.08582163, -3.071113, -0.005670905,
                                   -0.3674703, 12.09506, 1.254596,  -0.3040385,  -0.408572, 8.873255};
    EXPECT_THAT(dq.ip_dequant->cells().template typify<CT>(), Pointwise(DoubleNear(0.00001), ip_expected));
}

} // namespace

TEST(TensorQuantizationTest, can_quantize_and_dequantize_double_dense_indexed_tensors) {
    do_test_4_bit_quantized_roundtrip_of_dense_indexed_floating_point_tensor<double>();
}

TEST(TensorQuantizationTest, can_quantize_and_dequantize_float_dense_indexed_tensors) {
    do_test_4_bit_quantized_roundtrip_of_dense_indexed_floating_point_tensor<float>();
}

TEST(TensorQuantizationTest, can_quantize_and_dequantize_bfloat16_dense_indexed_tensors) {
    do_test_4_bit_quantized_roundtrip_of_dense_indexed_floating_point_tensor<BFloat16>();
}

TEST(TensorQuantizationTest, can_quantize_and_dequantize_int8_dense_indexed_tensors) {
    auto dq = quantize_indexed_tensor_roundtrip(CellType::INT8);
    // Original vector is {0, 12, 0, 0, 0, 9}
    // As an interesting point of comparison, conversion using static_cast (flooring) rather
    // than std::round (nearest) would yield {0, 11, 1, 0, 0, 8} instead, which has an absolute
    // error of 3 rather than 1.
    std::vector<Int8Float> mse_expected = {0, 12, 1, 0, 0, 9};
    EXPECT_THAT(dq.mse_dequant->cells().typify<Int8Float>(), Pointwise(Eq(), mse_expected));

    std::vector<Int8Float> ip_expected = {0, 12, 1, 0, 0, 9};
    EXPECT_THAT(dq.ip_dequant->cells().typify<Int8Float>(), Pointwise(Eq(), ip_expected));
}

TEST(TensorQuantizationTest, can_quantize_and_dequantize_double_mixed_tensors) {
    do_test_4_bit_quantized_roundtrip_of_mixed_floating_point_tensor<double>();
}

TEST(TensorQuantizationTest, can_quantize_and_dequantize_float_mixed_tensors) {
    do_test_4_bit_quantized_roundtrip_of_mixed_floating_point_tensor<float>();
}

TEST(TensorQuantizationTest, can_quantize_and_dequantize_bfloat16_mixed_tensors) {
    do_test_4_bit_quantized_roundtrip_of_mixed_floating_point_tensor<BFloat16>();
}

TEST(TensorQuantizationTest, can_quantize_and_dequantize_int8_mixed_tensors) {
    auto dq = quantize_mixed_tensor_roundtrip(CellType::INT8, 4);
    // Original vector is {0, 5, 0, 0, -3, 0, 0, 12, 0, 0, 0, 9}
    // In this case, using static_cast (flooring) rather than std::round (nearest) would yield
    // {0, 4, 0, 0, -3, 0, 0, 11, 1, 0, 0, 8} instead, with an absolute error of 4 rather than 1.
    std::vector<Int8Float> mse_expected = {0, 5, 0, 0, -3, 0, 0, 12, 1, 0, 0, 9};
    EXPECT_THAT(dq.mse_dequant->cells().typify<Int8Float>(), Pointwise(Eq(), mse_expected));

    std::vector<Int8Float> ip_expected = {0, 5, 0, 0, -3, 0, 0, 12, 1, 0, 0, 9};
    EXPECT_THAT(dq.ip_dequant->cells().typify<Int8Float>(), Pointwise(Eq(), ip_expected));
}

} // namespace search::tensor
