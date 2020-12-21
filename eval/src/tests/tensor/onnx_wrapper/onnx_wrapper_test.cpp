// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/onnx/onnx_wrapper.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;

using vespalib::make_string_short::fmt;
using TensorInfo = Onnx::TensorInfo;
using ElementType = Onnx::ElementType;
using DZ = Onnx::DimSize;

std::string get_source_dir() {
    const char *dir = getenv("SOURCE_DIRECTORY");
    return (dir ? dir : ".");
}
std::string source_dir = get_source_dir();
std::string simple_model = source_dir + "/simple.onnx";
std::string dynamic_model = source_dir + "/dynamic.onnx";
std::string int_types_model = source_dir + "/int_types.onnx";
std::string guess_batch_model = source_dir + "/guess_batch.onnx";

void dump_info(const char *ctx, const std::vector<TensorInfo> &info) {
    fprintf(stderr, "%s:\n", ctx);
    for (size_t i = 0; i < info.size(); ++i) {
        fprintf(stderr, "  %s[%zu]: '%s' %s\n", ctx, i, info[i].name.c_str(), info[i].type_as_string().c_str());
    }
}

TEST(WirePlannerTest, known_dimension_sizes_must_match) {
    Onnx::WirePlanner planner;
    ValueType type1 = ValueType::from_spec("tensor<float>(a[5],b[10])");
    ValueType type2 = ValueType::from_spec("tensor<float>(a[10],b[5])");
    ValueType type3 = ValueType::from_spec("tensor<float>(a[5],b[5])");
    TensorInfo info = TensorInfo{"info", {DZ(5),DZ(5)}, ElementType::FLOAT};
    EXPECT_FALSE(planner.bind_input_type(type1, info));
    EXPECT_FALSE(planner.bind_input_type(type2, info));
    EXPECT_TRUE(planner.bind_input_type(type3, info));
}

TEST(WirePlannerTest, symbolic_dimension_sizes_must_match) {
    Onnx::WirePlanner planner;
    ValueType type1 = ValueType::from_spec("tensor<float>(a[5])");
    ValueType type2 = ValueType::from_spec("tensor<float>(a[10])");
    TensorInfo info = TensorInfo{"info", {DZ("dim")}, ElementType::FLOAT};
    EXPECT_TRUE(planner.bind_input_type(type1, info)); // binds 'dim' to 5
    EXPECT_FALSE(planner.bind_input_type(type2, info));
    EXPECT_TRUE(planner.bind_input_type(type1, info));
}

TEST(WirePlannerTest, unknown_dimension_sizes_match_anything) {
    Onnx::WirePlanner planner;
    ValueType type1 = ValueType::from_spec("tensor<float>(a[5])");
    ValueType type2 = ValueType::from_spec("tensor<float>(a[10])");
    TensorInfo info = TensorInfo{"info", {DZ()}, ElementType::FLOAT};
    EXPECT_TRUE(planner.bind_input_type(type1, info));
    EXPECT_TRUE(planner.bind_input_type(type2, info));
}

TEST(WirePlannerTest, all_output_dimensions_must_be_bound) {
    Onnx::WirePlanner planner;
    ValueType type = ValueType::from_spec("tensor<float>(a[5],b[10])");
    TensorInfo info1 = TensorInfo{"info", {DZ()}, ElementType::FLOAT};
    TensorInfo info2 = TensorInfo{"info", {DZ("dim")}, ElementType::FLOAT};
    TensorInfo info3 = TensorInfo{"info", {DZ("dim"),DZ()}, ElementType::FLOAT};
    EXPECT_TRUE(planner.make_output_type(info1).is_error());
    EXPECT_TRUE(planner.make_output_type(info2).is_error());
    EXPECT_TRUE(planner.make_output_type(info3).is_error());
    EXPECT_TRUE(planner.bind_input_type(type, info3)); // binds 'dim' to 5
    EXPECT_TRUE(planner.make_output_type(info1).is_error());
    EXPECT_EQ(planner.make_output_type(info2).to_spec(), "tensor<float>(d0[5])");
    EXPECT_TRUE(planner.make_output_type(info3).is_error());
}

TEST(WirePlannerTest, dimensions_resolve_left_to_right) {
    Onnx::WirePlanner planner;
    ValueType type1 = ValueType::from_spec("tensor<float>(a[5],b[10])");
    ValueType type2 = ValueType::from_spec("tensor<float>(a[10],b[10])");
    ValueType type3 = ValueType::from_spec("tensor<float>(a[5],b[5])");
    TensorInfo info = TensorInfo{"info", {DZ("dim"),DZ("dim")}, ElementType::FLOAT};
    EXPECT_FALSE(planner.bind_input_type(type1, info)); // binds 'dim' to 5, then fails (5 != 10)
    EXPECT_FALSE(planner.bind_input_type(type2, info));
    EXPECT_TRUE(planner.bind_input_type(type3, info));
}

TEST(OnnxTest, simple_onnx_model_can_be_inspected)
{
    Onnx model(simple_model, Onnx::Optimize::DISABLE);
    dump_info("inputs", model.inputs());
    dump_info("outputs", model.outputs());
    ASSERT_EQ(model.inputs().size(), 3);
    ASSERT_EQ(model.outputs().size(), 1);
    //-------------------------------------------------------------------------
    EXPECT_EQ(model.inputs()[0].name,             "query_tensor");
    EXPECT_EQ(model.inputs()[0].type_as_string(), "float[1][4]");
    //-------------------------------------------------------------------------
    EXPECT_EQ(model.inputs()[1].name,             "attribute_tensor");
    EXPECT_EQ(model.inputs()[1].type_as_string(), "float[4][1]");
    //-------------------------------------------------------------------------
    EXPECT_EQ(model.inputs()[2].name,             "bias_tensor");
    EXPECT_EQ(model.inputs()[2].type_as_string(), "float[1][1]");
    //-------------------------------------------------------------------------
    EXPECT_EQ(model.outputs()[0].name,             "output");
    EXPECT_EQ(model.outputs()[0].type_as_string(), "float[1][1]");
}

TEST(OnnxTest, dynamic_onnx_model_can_be_inspected)
{
    Onnx model(dynamic_model, Onnx::Optimize::DISABLE);
    dump_info("inputs", model.inputs());
    dump_info("outputs", model.outputs());
    ASSERT_EQ(model.inputs().size(), 3);
    ASSERT_EQ(model.outputs().size(), 1);
    //-------------------------------------------------------------------------
    EXPECT_EQ(model.inputs()[0].name,             "query_tensor");
    EXPECT_EQ(model.inputs()[0].type_as_string(), "float[batch][4]");
    //-------------------------------------------------------------------------
    EXPECT_EQ(model.inputs()[1].name,             "attribute_tensor");
    EXPECT_EQ(model.inputs()[1].type_as_string(), "float[4][1]");
    //-------------------------------------------------------------------------
    EXPECT_EQ(model.inputs()[2].name,             "bias_tensor");
    EXPECT_EQ(model.inputs()[2].type_as_string(), "float[batch][]");
    //-------------------------------------------------------------------------
    EXPECT_EQ(model.outputs()[0].name,             "output");
    EXPECT_EQ(model.outputs()[0].type_as_string(), "float[batch][1]");
}

TEST(OnnxTest, simple_onnx_model_can_be_evaluated)
{
    Onnx model(simple_model, Onnx::Optimize::ENABLE);
    Onnx::WirePlanner planner;

    ValueType query_type = ValueType::from_spec("tensor<float>(a[1],b[4])");
    std::vector<float> query_values({1.0, 2.0, 3.0, 4.0});
    DenseValueView query(query_type, TypedCells(query_values));
    EXPECT_TRUE(planner.bind_input_type(query_type, model.inputs()[0]));

    ValueType attribute_type = ValueType::from_spec("tensor<float>(a[4],b[1])");
    std::vector<float> attribute_values({5.0, 6.0, 7.0, 8.0});
    DenseValueView attribute(attribute_type, TypedCells(attribute_values));
    EXPECT_TRUE(planner.bind_input_type(attribute_type, model.inputs()[1]));

    ValueType bias_type = ValueType::from_spec("tensor<float>(a[1],b[1])");
    std::vector<float> bias_values({9.0});
    DenseValueView bias(bias_type, TypedCells(bias_values));
    EXPECT_TRUE(planner.bind_input_type(bias_type, model.inputs()[2]));

    EXPECT_EQ(planner.make_output_type(model.outputs()[0]).to_spec(),
              "tensor<float>(d0[1],d1[1])");

    Onnx::WireInfo wire_info = planner.get_wire_info(model);
    Onnx::EvalContext ctx(model, wire_info);

    const Value &output = ctx.get_result(0);
    EXPECT_EQ(output.type().to_spec(), "tensor<float>(d0[1],d1[1])");
    //-------------------------------------------------------------------------
    ctx.bind_param(0, query);
    ctx.bind_param(1, attribute);
    ctx.bind_param(2, bias);
    ctx.eval();
    auto cells = output.cells();
    EXPECT_EQ(cells.type, CellType::FLOAT);
    EXPECT_EQ(cells.size, 1);
    EXPECT_EQ(cells.typify<float>()[0], 79.0);
    //-------------------------------------------------------------------------
    std::vector<float> new_bias_values({10.0});
    DenseValueView new_bias(bias_type, TypedCells(new_bias_values));
    ctx.bind_param(2, new_bias);
    ctx.eval();
    EXPECT_EQ(output.cells().typify<float>()[0], 80.0);
    //-------------------------------------------------------------------------
}

TEST(OnnxTest, dynamic_onnx_model_can_be_evaluated)
{
    Onnx model(dynamic_model, Onnx::Optimize::ENABLE);
    Onnx::WirePlanner planner;

    ValueType query_type = ValueType::from_spec("tensor<float>(a[1],b[4])");
    std::vector<float> query_values({1.0, 2.0, 3.0, 4.0});
    DenseValueView query(query_type, TypedCells(query_values));
    EXPECT_TRUE(planner.bind_input_type(query_type, model.inputs()[0]));

    ValueType attribute_type = ValueType::from_spec("tensor<float>(a[4],b[1])");
    std::vector<float> attribute_values({5.0, 6.0, 7.0, 8.0});
    DenseValueView attribute(attribute_type, TypedCells(attribute_values));
    EXPECT_TRUE(planner.bind_input_type(attribute_type, model.inputs()[1]));

    ValueType bias_type = ValueType::from_spec("tensor<float>(a[1],b[2])");
    std::vector<float> bias_values({4.0, 5.0});
    DenseValueView bias(bias_type, TypedCells(bias_values));
    EXPECT_TRUE(planner.bind_input_type(bias_type, model.inputs()[2]));

    EXPECT_EQ(planner.make_output_type(model.outputs()[0]).to_spec(),
              "tensor<float>(d0[1],d1[1])");

    Onnx::WireInfo wire_info = planner.get_wire_info(model);
    Onnx::EvalContext ctx(model, wire_info);

    const Value &output = ctx.get_result(0);
    EXPECT_EQ(output.type().to_spec(), "tensor<float>(d0[1],d1[1])");
    //-------------------------------------------------------------------------
    ctx.bind_param(0, query);
    ctx.bind_param(1, attribute);
    ctx.bind_param(2, bias);
    ctx.eval();
    auto cells = output.cells();
    EXPECT_EQ(cells.type, CellType::FLOAT);
    EXPECT_EQ(cells.size, 1);
    EXPECT_EQ(cells.typify<float>()[0], 79.0);
    //-------------------------------------------------------------------------
    std::vector<float> new_bias_values({5.0,6.0});
    DenseValueView new_bias(bias_type, TypedCells(new_bias_values));
    ctx.bind_param(2, new_bias);
    ctx.eval();
    EXPECT_EQ(output.cells().typify<float>()[0], 81.0);
    //-------------------------------------------------------------------------
}

TEST(OnnxTest, int_types_onnx_model_can_be_evaluated)
{
    Onnx model(int_types_model, Onnx::Optimize::ENABLE);
    Onnx::WirePlanner planner;

    ValueType query_type = ValueType::from_spec("tensor<float>(a[1],b[4])");
    std::vector<float> query_values({1.0, 2.0, 3.0, 4.0});
    DenseValueView query(query_type, TypedCells(query_values));
    EXPECT_TRUE(planner.bind_input_type(query_type, model.inputs()[0]));

    ValueType attribute_type = ValueType::from_spec("tensor<double>(a[4],b[1])");
    std::vector<double> attribute_values({5.0, 6.0, 7.0, 8.0});
    DenseValueView attribute(attribute_type, TypedCells(attribute_values));
    EXPECT_TRUE(planner.bind_input_type(attribute_type, model.inputs()[1]));

    ValueType bias_type = ValueType::from_spec("tensor<double>(a[1],b[1])");
    std::vector<double> bias_values({9.0});
    DenseValueView bias(bias_type, TypedCells(bias_values));
    EXPECT_TRUE(planner.bind_input_type(bias_type, model.inputs()[2]));

    EXPECT_EQ(planner.make_output_type(model.outputs()[0]),
              ValueType::from_spec("tensor<double>(d0[1],d1[1])"));

    Onnx::WireInfo wire_info = planner.get_wire_info(model);
    Onnx::EvalContext ctx(model, wire_info);

    const Value &output = ctx.get_result(0);
    EXPECT_EQ(output.type(), ValueType::from_spec("tensor<double>(d0[1],d1[1])"));
    //-------------------------------------------------------------------------
    ctx.bind_param(0, query);
    ctx.bind_param(1, attribute);
    ctx.bind_param(2, bias);
    ctx.eval();
    auto cells = output.cells();
    EXPECT_EQ(cells.type, CellType::DOUBLE);
    EXPECT_EQ(cells.size, 1);
    EXPECT_EQ(cells.typify<double>()[0], 79.0);
    //-------------------------------------------------------------------------
    std::vector<double> new_bias_values({10.0});
    DenseValueView new_bias(bias_type, TypedCells(new_bias_values));
    ctx.bind_param(2, new_bias);
    ctx.eval();
    EXPECT_EQ(output.cells().typify<double>()[0], 80.0);
    //-------------------------------------------------------------------------
}

TEST(OnnxTest, we_guess_batch_dimension_size_when_inference_fails) {    
    Onnx model(guess_batch_model, Onnx::Optimize::ENABLE);
    Onnx::WirePlanner planner_3;
    Onnx::WirePlanner planner_4;

    ValueType in_3_type = ValueType::from_spec("tensor<float>(a[3])");
    std::vector<float> in_3_values({1.0, 2.0, 3.0});
    DenseValueView in_3(in_3_type, TypedCells(in_3_values));
    EXPECT_TRUE(planner_3.bind_input_type(in_3_type, model.inputs()[0]));
    EXPECT_TRUE(planner_3.bind_input_type(in_3_type, model.inputs()[1]));

    ValueType in_4_type = ValueType::from_spec("tensor<float>(a[4])");
    std::vector<float> in_4_values({1.0, 2.0, 3.0, 4.0});
    DenseValueView in_4(in_4_type, TypedCells(in_4_values));
    EXPECT_TRUE(planner_4.bind_input_type(in_4_type, model.inputs()[0]));
    EXPECT_TRUE(planner_4.bind_input_type(in_4_type, model.inputs()[1]));

    EXPECT_EQ(planner_3.make_output_type(model.outputs()[0]).to_spec(), "tensor<float>(d0[3])");
    EXPECT_EQ(planner_4.make_output_type(model.outputs()[0]).to_spec(), "tensor<float>(d0[4])");

    Onnx::WireInfo wire_info_3 = planner_3.get_wire_info(model);
    Onnx::WireInfo wire_info_4 = planner_4.get_wire_info(model);
    Onnx::EvalContext ctx_3(model, wire_info_3);
    Onnx::EvalContext ctx_4(model, wire_info_4);
    //-------------------------------------------------------------------------
    ctx_3.bind_param(0, in_3);
    ctx_3.bind_param(1, in_3);
    ctx_3.eval();
    ctx_4.bind_param(0, in_4);
    ctx_4.bind_param(1, in_4);
    ctx_4.eval();
    //-------------------------------------------------------------------------
    auto out_3 = TensorSpec::from_value(ctx_3.get_result(0));
    auto out_4 = TensorSpec::from_value(ctx_4.get_result(0));
    auto expect_3 = TensorSpec::from_expr("tensor<float>(d0[3]):[2,4,6]");
    auto expect_4 = TensorSpec::from_expr("tensor<float>(d0[4]):[2,4,6,8]");
    EXPECT_EQ(out_3, expect_3);
    EXPECT_EQ(out_4, expect_4);
    //-------------------------------------------------------------------------
}

GTEST_MAIN_RUN_ALL_TESTS()
