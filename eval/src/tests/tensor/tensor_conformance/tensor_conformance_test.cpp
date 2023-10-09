// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/data/slime/slime.h>

using vespalib::make_string_short::fmt;
using vespalib::Slime;
using vespalib::slime::JsonFormat;
using vespalib::MappedFileInput;

vespalib::string module_build_path("../../../../");

TEST("require that (some) cross-language tensor conformance tests pass with C++ expression evaluation") {
    vespalib::string result_file = "conformance_result.json";
    vespalib::string binary = module_build_path + "src/apps/tensor_conformance/vespa-tensor-conformance";
    EXPECT_EQUAL(system(fmt("%s generate-some | %s evaluate | %s verify > %s", binary.c_str(), binary.c_str(), binary.c_str(), result_file.c_str()).c_str()), 0);
    Slime result;
    MappedFileInput input(result_file);
    JsonFormat::decode(input, result);
    fprintf(stderr, "conformance summary: %s\n", result.toString().c_str());
    int num_tests = result["num_tests"].asLong();
    int prod_tests = result["stats"]["cpp_prod"].asLong();
    int simple_tests = result["stats"]["cpp_simple_value"].asLong();
    int streamed_tests = result["stats"]["cpp_streamed_value"].asLong();
    EXPECT_TRUE(result["fail_cnt"].valid());
    EXPECT_EQUAL(result["fail_cnt"].asLong(), 0);
    EXPECT_TRUE(result["ignore_cnt"].valid());
    EXPECT_EQUAL(result["ignore_cnt"].asLong(), 0);
    EXPECT_GREATER(num_tests, 1000);
    EXPECT_EQUAL(prod_tests, num_tests);
    EXPECT_EQUAL(simple_tests, num_tests);
    EXPECT_EQUAL(streamed_tests, num_tests);
}

TEST_MAIN() { TEST_RUN_ALL(); }
