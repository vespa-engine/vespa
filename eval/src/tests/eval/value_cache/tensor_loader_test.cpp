// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/value_cache/constant_tensor_loader.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/eval/tensor_spec.h>

using namespace vespalib::eval;

std::unique_ptr<Tensor> dense_tensor_nocells() {
    return SimpleTensorEngine::ref()
        .create(TensorSpec("tensor(x[2],y[2])"));
}

std::unique_ptr<Tensor> make_nodim_tensor() {
    return SimpleTensorEngine::ref()
        .create(TensorSpec("double"));
}

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

void verify_tensor(std::unique_ptr<Tensor> expect, ConstantValue::UP actual) {
    const auto &engine = expect->engine();
    ASSERT_EQUAL(engine.type_of(*expect), actual->type());
    EXPECT_TRUE(&engine == &actual->value().as_tensor()->engine());
    EXPECT_TRUE(engine.equal(*expect, *actual->value().as_tensor()));
}

TEST_F("require that invalid types loads an empty double", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(make_nodim_tensor(), f1.create(TEST_PATH("dense.json"), "invalid type spec")));
}

TEST_F("require that invalid file name loads an empty tensor", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(dense_tensor_nocells(), f1.create(TEST_PATH("missing_file.json"), "tensor(x[2],y[2])")));
}

TEST_F("require that invalid json loads an empty tensor", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(dense_tensor_nocells(), f1.create(TEST_PATH("invalid.json"), "tensor(x[2],y[2])")));
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

TEST_F("require that lz4 compressed sparse tensor can be loaded", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(make_sparse_tensor(), f1.create(TEST_PATH("sparse.json.lz4"), "tensor(x{},y{})")));
}

TEST_F("require that bad lz4 file fails to load creating empty result", ConstantTensorLoader(SimpleTensorEngine::ref())) {
    TEST_DO(verify_tensor(dense_tensor_nocells(), f1.create(TEST_PATH("bad_lz4.json.lz4"), "tensor(x[2],y[2])")));
}

TEST_MAIN() { TEST_RUN_ALL(); }
