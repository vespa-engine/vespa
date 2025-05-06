// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/data/slime/slime.h>

using vespalib::make_string_short::fmt;
using vespalib::Slime;
using vespalib::slime::JsonFormat;
using vespalib::MappedFileInput;

std::string module_build_path("../../../../");

TEST(TensorConformanceTest, require_that_cross_language_tensor_conformance_tests_pass_with_CXX_expression_evaluation) {
    std::string result_file = "conformance_result.json";
    std::string binary = module_build_path + "src/apps/tensor_conformance/vespa-tensor-conformance";
    EXPECT_EQ(system(fmt("%s generate-some | %s evaluate | %s verify > %s", binary.c_str(), binary.c_str(), binary.c_str(), result_file.c_str()).c_str()), 0);
    Slime result;
    MappedFileInput input(result_file);
    JsonFormat::decode(input, result);
    fprintf(stderr, "conformance summary: %s\n", result.toString().c_str());
    int num_tests = result["num_tests"].asLong();
    int prod_tests = result["stats"]["cpp_prod"].asLong();
    int simple_tests = result["stats"]["cpp_simple_value"].asLong();
    int streamed_tests = result["stats"]["cpp_streamed_value"].asLong();
    EXPECT_TRUE(result["fail_cnt"].valid());
    EXPECT_EQ(result["fail_cnt"].asLong(), 0);
    EXPECT_TRUE(result["ignore_cnt"].valid());
    EXPECT_EQ(result["ignore_cnt"].asLong(), 0);
    EXPECT_GT(num_tests, 1000);
    EXPECT_EQ(prod_tests, num_tests);
    EXPECT_EQ(simple_tests, num_tests);
    EXPECT_EQ(streamed_tests, num_tests);
}

GTEST_MAIN_RUN_ALL_TESTS()
