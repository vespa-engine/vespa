// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/value_cache/constant_tensor_loader.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/tensor.h>

using namespace vespalib::eval;

TensorSpec sparse_tensor_nocells() {
    return TensorSpec("tensor(x{},y{})");
}

TensorSpec make_dense_tensor() {
    return TensorSpec("tensor(x[2],y[2])")
        .add({{"x", 0}, {"y", 0}}, 1.0)
        .add({{"x", 0}, {"y", 1}}, 2.0)
        .add({{"x", 1}, {"y", 0}}, 3.0)
        .add({{"x", 1}, {"y", 1}}, 4.0);
}

TensorSpec make_sparse_tensor() {
    return TensorSpec("tensor(x{},y{})")
        .add({{"x", "foo"}, {"y", "bar"}}, 1.0)
        .add({{"x", "bar"}, {"y", "foo"}}, 2.0);
}

TensorSpec make_mixed_tensor() {
    return TensorSpec("tensor(x{},y[2])")
        .add({{"x", "foo"}, {"y", 0}}, 1.0)
        .add({{"x", "foo"}, {"y", 1}}, 2.0);
}

void verify_tensor(const TensorSpec &expect, ConstantValue::UP actual) {
    const auto &engine = SimpleTensorEngine::ref();
    ASSERT_EQUAL(expect.type(), actual->type().to_spec());
    ASSERT_TRUE(&engine == &actual->value().as_tensor()->engine());
    EXPECT_EQUAL(expect, engine.to_spec(actual->value()));
}

void verify_invalid(ConstantValue::UP actual) {
    EXPECT_EQUAL(actual->type(), ValueType::double_type());
    EXPECT_EQUAL(actual->value().as_double(), 0.0);
}

TEST_F("require that invalid types loads an empty double", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_invalid(f1.create(TEST_PATH("dense.json"), "invalid type spec")));
}

TEST_F("require that invalid file name loads an empty tensor", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(sparse_tensor_nocells(), f1.create(TEST_PATH("missing_file.json"), "tensor(x{},y{})")));
}

TEST_F("require that invalid json loads an empty tensor", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(sparse_tensor_nocells(), f1.create(TEST_PATH("invalid.json"), "tensor(x{},y{})")));
}

TEST_F("require that dense tensors can be loaded", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(make_dense_tensor(), f1.create(TEST_PATH("dense.json"), "tensor(x[2],y[2])")));
}

TEST_F("require that mixed tensors can be loaded", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(make_mixed_tensor(), f1.create(TEST_PATH("mixed.json"), "tensor(x{},y[2])")));
}

TEST_F("require that lz4 compressed dense tensor can be loaded", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(make_dense_tensor(), f1.create(TEST_PATH("dense.json.lz4"), "tensor(x[2],y[2])")));
}

TEST_F("require that a binary tensor can be loaded", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(make_dense_tensor(), f1.create(TEST_PATH("dense.tbf"), "tensor(x[2],y[2])")));
}

TEST_F("require that lz4 compressed sparse tensor can be loaded", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(make_sparse_tensor(), f1.create(TEST_PATH("sparse.json.lz4"), "tensor(x{},y{})")));
}

TEST_F("require that bad lz4 file fails to load creating empty result", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(sparse_tensor_nocells(), f1.create(TEST_PATH("bad_lz4.json.lz4"), "tensor(x{},y{})")));
}

TEST_MAIN() { TEST_RUN_ALL(); }
