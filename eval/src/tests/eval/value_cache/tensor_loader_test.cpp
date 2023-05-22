// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/value_cache/constant_tensor_loader.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/tensor_spec.h>

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

TensorSpec make_simple_dense_tensor() {
    return TensorSpec("tensor(z[3])")
        .add({{"z", 0}}, 1.0)
        .add({{"z", 1}}, 2.0)
        .add({{"z", 2}}, 3.5);
}

TensorSpec make_sparse_tensor() {
    return TensorSpec("tensor(x{},y{})")
        .add({{"x", "foo"}, {"y", "bar"}}, 1.0)
        .add({{"x", "bar"}, {"y", "foo"}}, 2.0);
}

TensorSpec make_simple_sparse_tensor() {
    return TensorSpec("tensor(mydim{})")
        .add({{"mydim", "foo"}}, 1.0)
        .add({{"mydim", "three"}}, 3.0)
        .add({{"mydim", "bar"}}, 2.0);
}

TensorSpec make_mixed_tensor() {
    return TensorSpec("tensor(x{},y[2])")
        .add({{"x", "foo"}, {"y", 0}}, 1.0)
        .add({{"x", "foo"}, {"y", 1}}, 2.0);
}

const auto &factory = SimpleValueBuilderFactory::get();

void verify_tensor(const TensorSpec &expect, ConstantValue::UP actual) {
    ASSERT_EQUAL(expect.type(), actual->type().to_spec());
    EXPECT_TRUE(dynamic_cast<const SimpleValue *>(&actual->value()));
    EXPECT_EQUAL(expect, spec_from_value(actual->value()));
}

void verify_invalid(ConstantValue::UP actual) {
    EXPECT_TRUE(actual->type().is_error());
}

TEST_F("require that invalid types gives bad constant value", ConstantTensorLoader(factory)) {
    TEST_DO(verify_invalid(f1.create(TEST_PATH("dense.json"), "invalid type spec")));
}

TEST_F("require that invalid file name loads an empty tensor", ConstantTensorLoader(factory)) {
    TEST_DO(verify_tensor(sparse_tensor_nocells(), f1.create(TEST_PATH("missing_file.json"), "tensor(x{},y{})")));
}

TEST_F("require that invalid json loads an empty tensor", ConstantTensorLoader(factory)) {
    TEST_DO(verify_tensor(sparse_tensor_nocells(), f1.create(TEST_PATH("invalid.json"), "tensor(x{},y{})")));
}

TEST_F("require that dense tensors can be loaded", ConstantTensorLoader(factory)) {
    TEST_DO(verify_tensor(make_dense_tensor(), f1.create(TEST_PATH("dense.json"), "tensor(x[2],y[2])")));
}

TEST_F("require that mixed tensors can be loaded", ConstantTensorLoader(factory)) {
    TEST_DO(verify_tensor(make_mixed_tensor(), f1.create(TEST_PATH("mixed.json"), "tensor(x{},y[2])")));
}

TEST_F("require that lz4 compressed dense tensor can be loaded", ConstantTensorLoader(factory)) {
    TEST_DO(verify_tensor(make_dense_tensor(), f1.create(TEST_PATH("dense.json.lz4"), "tensor(x[2],y[2])")));
}

TEST_F("require that a binary tensor can be loaded", ConstantTensorLoader(factory)) {
    TEST_DO(verify_tensor(make_dense_tensor(), f1.create(TEST_PATH("dense.tbf"), "tensor(x[2],y[2])")));
}

TEST_F("require that lz4 compressed sparse tensor can be loaded", ConstantTensorLoader(factory)) {
    TEST_DO(verify_tensor(make_sparse_tensor(), f1.create(TEST_PATH("sparse.json.lz4"), "tensor(x{},y{})")));
}

TEST_F("require that sparse tensor short form can be loaded", ConstantTensorLoader(factory)) {
    TEST_DO(verify_tensor(make_simple_sparse_tensor(), f1.create(TEST_PATH("sparse-short1.json"), "tensor(mydim{})")));
    TEST_DO(verify_tensor(make_simple_sparse_tensor(), f1.create(TEST_PATH("sparse-short2.json"), "tensor(mydim{})")));
}

TEST_F("require that dense tensor short form can be loaded", ConstantTensorLoader(factory)) {
    TEST_DO(verify_tensor(make_simple_dense_tensor(), f1.create(TEST_PATH("dense-short1.json"), "tensor(z[3])")));
    TEST_DO(verify_tensor(make_simple_dense_tensor(), f1.create(TEST_PATH("dense-short2.json"), "tensor(z[3])")));
}

TEST_F("require that bad lz4 file fails to load creating empty result", ConstantTensorLoader(factory)) {
    TEST_DO(verify_tensor(sparse_tensor_nocells(), f1.create(TEST_PATH("bad_lz4.json.lz4"), "tensor(x{},y{})")));
}

TEST_MAIN() { TEST_RUN_ALL(); }
