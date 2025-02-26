// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/test/test_io.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval::test;

std::string module_build_path("../../../../");
std::string binary = module_build_path + "src/apps/analyze_onnx_model/vespa-analyze-onnx-model";
std::string probe_cmd = binary + " --probe-types";

std::string get_source_dir() {
    const char *dir = getenv("SOURCE_DIRECTORY");
    return (dir ? dir : ".");
}
std::string source_dir = get_source_dir();
std::string probe_model = source_dir + "/../../tensor/onnx_wrapper/probe_model.onnx";
std::string simple_model = source_dir + "/../../tensor/onnx_wrapper/simple.onnx";
std::string dynamic_model = source_dir + "/../../tensor/onnx_wrapper/dynamic.onnx";

//-----------------------------------------------------------------------------

TEST(AnalyzeOnnxModelTest, require_that_output_types_can_be_probed)
{
    ServerCmd f1(probe_cmd);
    Slime params;
    params.setObject();
    params.get().setString("model", probe_model);
    params.get().setObject("inputs");
    params["inputs"].setString("in1", "tensor<float>(x[2],y[3])");
    params["inputs"].setString("in2", "tensor<float>(x[2],y[3])");
    Slime result = f1.invoke(params);
    EXPECT_EQ(result["outputs"].fields(), 3u);
    EXPECT_EQ(result["outputs"]["out1"].asString().make_string(), std::string("tensor<float>(d0[2],d1[3])"));
    EXPECT_EQ(result["outputs"]["out2"].asString().make_string(), std::string("tensor<float>(d0[2],d1[3])"));
    EXPECT_EQ(result["outputs"]["out3"].asString().make_string(), std::string("tensor<float>(d0[2],d1[3])"));
    EXPECT_EQ(f1.shutdown(), 0);
}

//-----------------------------------------------------------------------------

TEST(AnalyzeOnnxModelTest, test_error_invalid_json)
{
    ServerCmd f1(probe_cmd);
    auto out = f1.write_then_read_all("this is not valid json...\n");
    EXPECT_TRUE(out.find("invalid json") < out.size());
    EXPECT_EQ(f1.shutdown(), 3);
}

TEST(AnalyzeOnnxModelTest, test_error_missing_input_type)
{
    ServerCmd f1(probe_cmd);
    Slime params;
    params.setObject();
    params.get().setString("model", simple_model);
    params.get().setObject("inputs");
    auto out = f1.write_then_read_all(params.toString());
    EXPECT_TRUE(out.find("missing type") < out.size());
    EXPECT_EQ(f1.shutdown(), 3);
}

TEST(AnalyzeOnnxModelTest, test_error_invalid_input_type)
{
    ServerCmd f1(probe_cmd);
    Slime params;
    params.setObject();
    params.get().setString("model", simple_model);
    params.get().setObject("inputs");
    params["inputs"].setString("query_tensor", "bogus type string");
    params["inputs"].setString("attribute_tensor", "tensor<float>(x[4],y[1])");
    params["inputs"].setString("bias_tensor", "tensor<float>(x[1],y[1])");
    auto out = f1.write_then_read_all(params.toString());
    EXPECT_TRUE(out.find("invalid type") < out.size());
    EXPECT_EQ(f1.shutdown(), 3);
}

TEST(AnalyzeOnnxModelTest, test_error_incompatible_input_type)
{
    ServerCmd f1(probe_cmd);
    Slime params;
    params.setObject();
    params.get().setString("model", simple_model);
    params.get().setObject("inputs");
    params["inputs"].setString("query_tensor", "tensor<float>(x[1],y[5])");
    params["inputs"].setString("attribute_tensor", "tensor<float>(x[4],y[1])");
    params["inputs"].setString("bias_tensor", "tensor<float>(x[1],y[1])");
    auto out = f1.write_then_read_all(params.toString());
    EXPECT_TRUE(out.find("incompatible type") < out.size());
    EXPECT_EQ(f1.shutdown(), 3);
}

TEST(AnalyzeOnnxModelTest, test_error_symbolic_size_mismatch)
{
    ServerCmd f1(probe_cmd);
    Slime params;
    params.setObject();
    params.get().setString("model", dynamic_model);
    params.get().setObject("inputs");
    params["inputs"].setString("query_tensor", "tensor<float>(x[1],y[4])");
    params["inputs"].setString("attribute_tensor", "tensor<float>(x[4],y[1])");
    params["inputs"].setString("bias_tensor", "tensor<float>(x[2],y[1])");
    auto out = f1.write_then_read_all(params.toString());
    EXPECT_TRUE(out.find("incompatible type") < out.size());
    EXPECT_TRUE(out.find("batch=1") < out.size());
    EXPECT_EQ(f1.shutdown(), 3);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
