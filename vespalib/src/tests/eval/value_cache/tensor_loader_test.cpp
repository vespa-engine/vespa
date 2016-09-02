// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/eval/value_cache/constant_tensor_loader.h>
#include <vespa/vespalib/eval/simple_tensor_engine.h>
#include <vespa/vespalib/eval/tensor_spec.h>

using namespace vespalib::eval;

std::unique_ptr<Tensor> make_dense_tensor() {
    return SimpleTensorEngine::ref()
        .create(TensorSpec("tensor(x[2],y[2])")
                .add({{"x", 0}, {"y", 0}}, 1.0)
                .add({{"x", 0}, {"y", 1}}, 2.0)
                .add({{"x", 1}, {"y", 0}}, 3.0)
                .add({{"x", 1}, {"y", 1}}, 4.0));
}

std::unique_ptr<Tensor> make_sparse_tensor() {
    return SimpleTensorEngine::ref()
        .create(TensorSpec("tensor(x{},y{})")
                .add({{"x", "foo"}, {"y", "bar"}}, 1.0)
                .add({{"x", "bar"}, {"y", "foo"}}, 2.0));
}

std::unique_ptr<Tensor> make_mixed_tensor() {
    return SimpleTensorEngine::ref()
        .create(TensorSpec("tensor(x{},y[2])")
                .add({{"x", "foo"}, {"y", 0}}, 1.0)
                .add({{"x", "foo"}, {"y", 1}}, 2.0));
}

void verify_error(ConstantValue::UP actual) {
    EXPECT_TRUE(actual->type().is_error());
    EXPECT_TRUE(actual->value().is_error());
}

void verify_tensor(std::unique_ptr<Tensor> expect, ConstantValue::UP actual) {
    const auto &engine = expect->engine();
    ASSERT_EQUAL(engine.type_of(*expect), actual->type());
    EXPECT_TRUE(&engine == &actual->value().as_tensor()->engine());
    EXPECT_TRUE(engine.equal(*expect, *actual->value().as_tensor()));
}

TEST_F("require that load fails for invalid types", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_error(f1.create(vespalib::TestApp::GetSourceDirectory() + "dense.json", "invalid type spec")));
}

TEST_F("require that load fails for invalid file name", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_error(f1.create(vespalib::TestApp::GetSourceDirectory() + "missing_file.json", "tensor(x[2],y[2])")));
}

TEST_F("require that load fails for invalid json", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_error(f1.create(vespalib::TestApp::GetSourceDirectory() + "invalid.json", "tensor(x[2],y[2])")));
}

TEST_F("require that dense tensors can be loaded", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(make_dense_tensor(), f1.create(vespalib::TestApp::GetSourceDirectory() + "dense.json", "tensor(x[2],y[2])")));
}

TEST_F("require that sparse tensors can be loaded", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(make_sparse_tensor(), f1.create(vespalib::TestApp::GetSourceDirectory() + "sparse.json", "tensor(x{},y{})")));
}

TEST_F("require that mixed tensors can be loaded", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(make_mixed_tensor(), f1.create(vespalib::TestApp::GetSourceDirectory() + "mixed.json", "tensor(x{},y[2])")));
}

TEST_MAIN() { TEST_RUN_ALL(); }
