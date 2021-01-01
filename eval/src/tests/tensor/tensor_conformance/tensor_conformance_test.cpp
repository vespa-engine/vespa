// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/test/tensor_conformance.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/streamed/streamed_value_builder_factory.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/io/mapped_file_input.h>
#include <vespa/vespalib/data/slime/slime.h>

using vespalib::eval::SimpleValueBuilderFactory;
using vespalib::eval::StreamedValueBuilderFactory;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::test::TensorConformance;
using vespalib::make_string_short::fmt;
using vespalib::Slime;
using vespalib::slime::JsonFormat;
using vespalib::MappedFileInput;

vespalib::string module_src_path(TEST_PATH("../../../../"));
vespalib::string module_build_path("../../../../");

TEST("require that SimpleValue implementation passes all conformance tests") {
    TEST_DO(TensorConformance::run_tests(module_src_path, SimpleValueBuilderFactory::get()));
}

TEST("require that StreamedValue implementation passes all conformance tests") {
    TEST_DO(TensorConformance::run_tests(module_src_path, StreamedValueBuilderFactory::get()));
}

TEST("require that FastValue implementation passes all conformance tests") {
    TEST_DO(TensorConformance::run_tests(module_src_path, FastValueBuilderFactory::get()));
}

TEST("require that tensor serialization test spec can be generated") {
    vespalib::string spec = module_src_path + "src/apps/make_tensor_binary_format_test_spec/test_spec.json";
    vespalib::string binary = module_build_path + "src/apps/make_tensor_binary_format_test_spec/eval_make_tensor_binary_format_test_spec_app";
    EXPECT_EQUAL(system(fmt("%s > binary_test_spec.json", binary.c_str()).c_str()), 0);
    EXPECT_EQUAL(system(fmt("diff -u %s binary_test_spec.json", spec.c_str()).c_str()), 0);
}

TEST("require that cross-language tensor conformance tests pass with C++ expression evaluation") {
    vespalib::string result_file = "conformance_result.json";
    vespalib::string binary = module_build_path + "src/apps/tensor_conformance/vespa-tensor-conformance";
    EXPECT_EQUAL(system(fmt("%s generate | %s evaluate | %s verify > %s", binary.c_str(), binary.c_str(), binary.c_str(), result_file.c_str()).c_str()), 0);
    Slime result;
    MappedFileInput input(result_file);
    JsonFormat::decode(input, result);
    fprintf(stderr, "conformance summary: %s\n", result.toString().c_str());
    int num_tests = result.get()["num_tests"].asLong();
    int prod_tests = result.get()["stats"]["cpp_prod"].asLong();
    int simple_tests = result.get()["stats"]["cpp_simple_value"].asLong();
    int streamed_tests = result.get()["stats"]["cpp_streamed_value"].asLong();
    int with_expect = result.get()["stats"]["expect"].asLong();
    EXPECT_GREATER(num_tests, 1000);
    EXPECT_EQUAL(prod_tests, num_tests);
    EXPECT_EQUAL(simple_tests, num_tests);
    EXPECT_EQUAL(streamed_tests, num_tests);
    EXPECT_EQUAL(with_expect, num_tests);
}

TEST_MAIN() { TEST_RUN_ALL(); }
