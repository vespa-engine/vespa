// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/tensor/dense/onnx_wrapper.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/tensor/dense/mutable_dense_tensor_view.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::tensor;

using vespalib::make_string_short::fmt;

std::string get_source_dir() {
    const char *dir = getenv("SOURCE_DIRECTORY");
    return (dir ? dir : ".");
}
std::string source_dir = get_source_dir();
std::string vespa_dir = source_dir + "/" + "../../../../..";
std::string simple_model = vespa_dir + "/" + "model-integration/src/test/models/onnx/simple/simple.onnx";

vespalib::string to_str(const std::vector<size_t> &dim_sizes) {
    vespalib::string res;
    for (size_t dim_size: dim_sizes) {
        if (dim_size == 0) {
            res += "[]";
        } else {
            res += fmt("[%zu]", dim_size);
        }
    }
    return res;
}

vespalib::string to_str(OnnxWrapper::TensorInfo::ElementType element_type) {
    if (element_type == OnnxWrapper::TensorInfo::ElementType::FLOAT) {
        return "float";
    }
    if (element_type == OnnxWrapper::TensorInfo::ElementType::DOUBLE) {
        return "double";
    }
    return "???";
}

void dump_info(const char *ctx, const std::vector<OnnxWrapper::TensorInfo> &info) {
    fprintf(stderr, "%s:\n", ctx);
    for (size_t i = 0; i < info.size(); ++i) {
        fprintf(stderr, "  %s[%zu]: '%s' %s%s\n", ctx, i, info[i].name.c_str(),
                to_str(info[i].elements).c_str(),to_str(info[i].dimensions).c_str());
    }
}

TEST(OnnxWrapperTest, onnx_model_can_be_inspected)
{
    OnnxWrapper wrapper(simple_model, OnnxWrapper::Optimize::DISABLE);
    dump_info("inputs", wrapper.inputs());
    dump_info("outputs", wrapper.outputs());
    ASSERT_EQ(wrapper.inputs().size(), 3);
    ASSERT_EQ(wrapper.outputs().size(), 1);
    //-------------------------------------------------------------------------
    EXPECT_EQ(       wrapper.inputs()[0].name,        "query_tensor");
    EXPECT_EQ(to_str(wrapper.inputs()[0].dimensions), "[1][4]");
    EXPECT_EQ(to_str(wrapper.inputs()[0].elements),   "float");
    //-------------------------------------------------------------------------
    EXPECT_EQ(       wrapper.inputs()[1].name,        "attribute_tensor");
    EXPECT_EQ(to_str(wrapper.inputs()[1].dimensions), "[4][1]");
    EXPECT_EQ(to_str(wrapper.inputs()[1].elements),   "float");
    //-------------------------------------------------------------------------
    EXPECT_EQ(       wrapper.inputs()[2].name,        "bias_tensor");
    EXPECT_EQ(to_str(wrapper.inputs()[2].dimensions), "[1][1]");
    EXPECT_EQ(to_str(wrapper.inputs()[2].elements),   "float");
    //-------------------------------------------------------------------------
    EXPECT_EQ(       wrapper.outputs()[0].name,        "output");
    EXPECT_EQ(to_str(wrapper.outputs()[0].dimensions), "[1][1]");
    EXPECT_EQ(to_str(wrapper.outputs()[0].elements),   "float");
}

TEST(OnnxWrapperTest, onnx_model_can_be_evaluated)
{
    OnnxWrapper wrapper(simple_model, OnnxWrapper::Optimize::ENABLE);

    ValueType query_type = ValueType::from_spec("tensor<float>(a[1],b[4])");
    std::vector<float> query_values({1.0, 2.0, 3.0, 4.0});
    DenseTensorView query(query_type, TypedCells(query_values));
    EXPECT_TRUE(wrapper.inputs()[0].is_compatible(query_type));
    EXPECT_FALSE(wrapper.inputs()[1].is_compatible(query_type));
    EXPECT_FALSE(wrapper.inputs()[2].is_compatible(query_type));

    ValueType attribute_type = ValueType::from_spec("tensor<float>(a[4],b[1])");
    std::vector<float> attribute_values({5.0, 6.0, 7.0, 8.0});
    DenseTensorView attribute(attribute_type, TypedCells(attribute_values));
    EXPECT_FALSE(wrapper.inputs()[0].is_compatible(attribute_type));
    EXPECT_TRUE(wrapper.inputs()[1].is_compatible(attribute_type));
    EXPECT_FALSE(wrapper.inputs()[2].is_compatible(attribute_type));

    ValueType bias_type = ValueType::from_spec("tensor<float>(a[1],b[1])");
    std::vector<float> bias_values({9.0});
    DenseTensorView bias(bias_type, TypedCells(bias_values));
    EXPECT_FALSE(wrapper.inputs()[0].is_compatible(bias_type));
    EXPECT_FALSE(wrapper.inputs()[1].is_compatible(bias_type));
    EXPECT_TRUE(wrapper.inputs()[2].is_compatible(bias_type));

    MutableDenseTensorView output(wrapper.outputs()[0].make_compatible_type());
    EXPECT_EQ(output.fast_type().to_spec(), "tensor<float>(d0[1],d1[1])");

    OnnxWrapper::Params params;
    params.bind(0, query);
    params.bind(1, attribute);
    params.bind(2, bias);
    auto result = wrapper.eval(params);

    EXPECT_EQ(result.num_values(), 1);
    result.get(0, output);
    auto cells = output.cellsRef();
    EXPECT_EQ(cells.type, ValueType::CellType::FLOAT);
    EXPECT_EQ(cells.size, 1);
    EXPECT_EQ(cells.get(0), 79.0);
}

GTEST_MAIN_RUN_ALL_TESTS()
