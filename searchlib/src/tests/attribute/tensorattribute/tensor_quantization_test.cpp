// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/searchlib/tensor/tensor_quantization.h>
#include <vespa/vespalib/quant/eden.h>

#include <gmock/gmock.h>

#include <iostream>

using vespalib::eval::CellType;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::quant::EdenQuantizer;
using vespalib::quant::QuantMode;

using namespace ::testing;

namespace search::tensor {

namespace {

Value::UP create_tensor(const TensorSpec& spec) {
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

TEST(TensorQuantizationTest, can_quantize_and_dequantize_indexed_tensors) {
    constexpr uint8_t q_bits = 4;

    auto t = create_tensor(
        TensorSpec("tensor<float>(x[2],y[3])").add({{"x", 0}, {"y", 1}}, 12).add({{"x", 1}, {"y", 2}}, 9));
    auto q = make_quantizer_from_type(t->type(), q_bits);
    auto q_type = to_quantized_tensor_type(t->type(), *q);

    auto qt_mse = search::tensor::quantize_f32_tensor(*t, q_type, *q, QuantMode::MSE);
    auto dqt_mse = search::tensor::dequantize_tensor(*qt_mse, t->type(), *q);
    auto qt_ip = search::tensor::quantize_f32_tensor(*t, q_type, *q, QuantMode::InnerProduct);
    auto dqt_ip = search::tensor::dequantize_tensor(*qt_ip, t->type(), *q);

    EXPECT_EQ(qt_mse->type(), q_type);
    EXPECT_EQ(qt_ip->type(), q_type);

    // OG vector
    EXPECT_THAT(t->cells().typify<float>(), ElementsAre(/*x=0*/ 0, 12, 0, /*x=1*/ 0, 0, 9));
    // we have vector at home, MSE edition:
    std::vector<float> mse_expected = {-0.364243, 11.9888, 1.24358, -0.301369, -0.404984, 8.79533};
    EXPECT_THAT(dqt_mse->cells().typify<float>(), Pointwise(FloatNear(0.0001), mse_expected));
    // Inner-product edition:
    std::vector<float> ip_expected = {-0.36747, 12.0951, 1.2546, -0.304039, -0.408572, 8.87325};
    EXPECT_THAT(dqt_ip->cells().typify<float>(), Pointwise(FloatNear(0.0001), ip_expected));
}

TEST(TensorQuantizationTest, can_quantize_and_dequantize_mixed_tensors) {
    constexpr uint8_t q_bits = 4;

    auto t = create_tensor(TensorSpec("tensor<float>(a{},x[2],y[3])")
                               .add({{"a", "foo"}, {"x", 0}, {"y", 1}}, 12)
                               .add({{"a", "foo"}, {"x", 1}, {"y", 2}}, 9)
                               .add({{"a", "bar"}, {"x", 0}, {"y", 1}}, 5)
                               .add({{"a", "bar"}, {"x", 1}, {"y", 1}}, 3));
    auto q = make_quantizer_from_type(t->type(), q_bits);
    auto q_type = to_quantized_tensor_type(t->type(), *q);

    auto qt_mse = search::tensor::quantize_f32_tensor(*t, q_type, *q, QuantMode::MSE);
    auto dqt_mse = search::tensor::dequantize_tensor(*qt_mse, t->type(), *q);
    auto qt_ip = search::tensor::quantize_f32_tensor(*t, q_type, *q, QuantMode::InnerProduct);
    auto dqt_ip = search::tensor::dequantize_tensor(*qt_ip, t->type(), *q);

    EXPECT_EQ(qt_mse->type(), q_type);
    EXPECT_EQ(qt_ip->type(), q_type);

    // OG vector. Arranged as bar=[0, 5, 0, 0, 3, 0], foo=[0, 12, 0, 0, 0, 9]
    EXPECT_THAT(t->cells().typify<float>(), ElementsAre(0, 5, 0, 0, 3, 0, 0, 12, 0, 0, 0, 9));

    std::vector<float> mse_expected = {-0.00648499, 4.85099, 0.384633, -0.0835068, 3.1499,    -0.309387,
                                       -0.364243,   11.9888, 1.24358,  -0.301369,  -0.404984, 8.79533};
    EXPECT_THAT(dqt_mse->cells().typify<float>(), Pointwise(FloatNear(0.0001), mse_expected));

    std::vector<float> ip_expected = {-0.00654173, 4.8935,  0.388003, -0.0842385, 3.1775,    -0.312098,
                                      -0.36747,    12.0951, 1.2546,   -0.304039,  -0.408572, 8.87325};
    EXPECT_THAT(dqt_ip->cells().typify<float>(), Pointwise(FloatNear(0.0001), ip_expected));
}

} // namespace search::tensor
