// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/test/tensor_conformance.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/vespalib/util/stringfmt.h>

using vespalib::eval::SimpleValueBuilderFactory;
using vespalib::eval::FastValueBuilderFactory;
using vespalib::eval::SimpleTensorEngine;
using vespalib::eval::test::TensorConformance;
using vespalib::tensor::DefaultTensorEngine;
using vespalib::make_string;

vespalib::string module_src_path(TEST_PATH("../../../../"));
vespalib::string module_build_path("../../../../");

TEST("require that reference tensor implementation passes all conformance tests") {
    TEST_DO(TensorConformance::run_tests(module_src_path, SimpleTensorEngine::ref()));
}

TEST("require that production tensor implementation passes all conformance tests") {
    TEST_DO(TensorConformance::run_tests(module_src_path, DefaultTensorEngine::ref()));
}

TEST("require that SimpleValue implementation passes all conformance tests") {
    TEST_DO(TensorConformance::run_tests(module_src_path, SimpleValueBuilderFactory::get()));
}

TEST("require that FastValue implementation passes all conformance tests") {
    TEST_DO(TensorConformance::run_tests(module_src_path, FastValueBuilderFactory::get()));
}

TEST("require that tensor serialization test spec can be generated") {
    vespalib::string spec = module_src_path + "src/apps/make_tensor_binary_format_test_spec/test_spec.json";
    vespalib::string binary = module_build_path + "src/apps/make_tensor_binary_format_test_spec/eval_make_tensor_binary_format_test_spec_app";
    EXPECT_EQUAL(system(make_string("%s > binary_test_spec.json", binary.c_str()).c_str()), 0);
    EXPECT_EQUAL(system(make_string("diff -u %s binary_test_spec.json", spec.c_str()).c_str()), 0);
}

TEST("require that cross-language tensor conformance test spec can be generated") {
    vespalib::string spec = module_src_path + "src/apps/tensor_conformance/test_spec.json";
    vespalib::string binary = module_build_path + "src/apps/tensor_conformance/vespa-tensor-conformance";
    EXPECT_EQUAL(system(make_string("%s generate > conformance_test_spec.json", binary.c_str()).c_str()), 0);
    EXPECT_EQUAL(system(make_string("%s compare %s conformance_test_spec.json", binary.c_str(), spec.c_str()).c_str()), 0);
}

TEST("require that cross-language tensor conformance tests pass with production C++ expression evaluation") {
    vespalib::string spec = module_src_path + "src/apps/tensor_conformance/test_spec.json";
    vespalib::string binary = module_build_path + "src/apps/tensor_conformance/vespa-tensor-conformance";
    EXPECT_EQUAL(system(make_string("cat %s | %s evaluate | %s verify", spec.c_str(), binary.c_str(), binary.c_str()).c_str()), 0);
}

TEST_MAIN() { TEST_RUN_ALL(); }
