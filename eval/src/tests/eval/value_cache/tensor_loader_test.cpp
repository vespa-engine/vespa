// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/value_cache/constant_tensor_loader.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>

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
        .add({{"x", "17"}, {"y", "42"}}, 1742.0)
        .add({{"x", "foo"}, {"y", "bar"}}, 1.0)
        .add({{"x", "bar"}, {"y", "foo"}}, 2.0);
}

TensorSpec make_simple_sparse_tensor() {
    return TensorSpec("tensor(mydim{})")
        .add({{"mydim", "foo"}}, 1.0)
        .add({{"mydim", "cells"}}, 2.0)
        .add({{"mydim", "values"}}, 0.5)
        .add({{"mydim", "blocks"}}, 1.5);
}

TensorSpec make_mixed_tensor() {
    return TensorSpec("tensor(x{},y[2])")
        .add({{"x", "foo"}, {"y", 0}}, 1.0)
        .add({{"x", "foo"}, {"y", 1}}, 2.0);
}

const auto &factory = SimpleValueBuilderFactory::get();

void verify_tensor(const TensorSpec &expect, ConstantValue::UP actual) {
    ASSERT_EQ(expect.type(), actual->type().to_spec());
    EXPECT_TRUE(dynamic_cast<const SimpleValue *>(&actual->value()));
    EXPECT_EQ(expect, spec_from_value(actual->value()));
}

void verify_invalid(ConstantValue::UP actual) {
    EXPECT_TRUE(actual->type().is_error());
}

class TensorLoaderTest : public ::testing::Test {
protected:
    ConstantTensorLoader f1;
    TensorLoaderTest();
    ~TensorLoaderTest() override;
};

TensorLoaderTest::TensorLoaderTest()
    : ::testing::Test(),
      f1(factory)
{
}

TensorLoaderTest::~TensorLoaderTest() = default;

TEST_F(TensorLoaderTest, require_that_invalid_types_gives_bad_constant_value)
{
    verify_invalid(f1.create(TEST_PATH("dense.json"), "invalid type spec"));
}

TEST_F(TensorLoaderTest, require_that_invalid_file_name_loads_an_empty_tensor)
{
    verify_tensor(sparse_tensor_nocells(), f1.create(TEST_PATH("missing_file.json"), "tensor(x{},y{})"));
}

TEST_F(TensorLoaderTest, require_that_invalid_json_loads_an_empty_tensor)
{
    verify_tensor(sparse_tensor_nocells(), f1.create(TEST_PATH("invalid.json"), "tensor(x{},y{})"));
}

TEST_F(TensorLoaderTest, require_that_dense_tensors_can_be_loaded)
{
    verify_tensor(make_dense_tensor(), f1.create(TEST_PATH("dense.json"), "tensor(x[2],y[2])"));
}

TEST_F(TensorLoaderTest, require_that_sparse_tensors_can_be_loaded)
{
    verify_tensor(make_sparse_tensor(), f1.create(TEST_PATH("sparse.json"), "tensor(x{},y{})"));
}

TEST_F(TensorLoaderTest, require_that_mixed_tensors_can_be_loaded) {
    verify_tensor(make_mixed_tensor(), f1.create(TEST_PATH("mixed.json"), "tensor(x{},y[2])"));
}

TEST_F(TensorLoaderTest, require_that_lz4_compressed_dense_tensor_can_be_loaded)
{
    verify_tensor(make_dense_tensor(), f1.create(TEST_PATH("dense.json.lz4"), "tensor(x[2],y[2])"));
}

TEST_F(TensorLoaderTest, require_that_a_binary_tensor_can_be_loaded)
{
    verify_tensor(make_dense_tensor(), f1.create(TEST_PATH("dense.tbf"), "tensor(x[2],y[2])"));
}

TEST_F(TensorLoaderTest, require_that_lz4_compressed_sparse_tensor_can_be_loaded)
{
    verify_tensor(make_sparse_tensor(), f1.create(TEST_PATH("sparse.json.lz4"), "tensor(x{},y{})"));
}

TEST_F(TensorLoaderTest, require_that_sparse_tensor_short_form_can_be_loaded)
{
    {
        SCOPED_TRACE("short1");
        verify_tensor(make_simple_sparse_tensor(), f1.create(TEST_PATH("sparse-short1.json"), "tensor(mydim{})"));
    }
    {
        SCOPED_TRACE("short2");
        verify_tensor(make_simple_sparse_tensor(), f1.create(TEST_PATH("sparse-short2.json"), "tensor(mydim{})"));
    }
}

TEST_F(TensorLoaderTest, require_that_dense_tensor_short_form_can_be_loaded)
{
    {
        SCOPED_TRACE("dense short1");
        verify_tensor(make_simple_dense_tensor(), f1.create(TEST_PATH("dense-short1.json"), "tensor(z[3])"));
    }
    {
        SCOPED_TRACE("dense short2");
        verify_tensor(make_simple_dense_tensor(), f1.create(TEST_PATH("dense-short2.json"), "tensor(z[3])"));
    }
}

TensorSpec make_mix21_tensor() {
    return TensorSpec("tensor<float>(brand{},category{},v[3])")
            .add({{"brand", "shiny"},   {"category", "foo"}, {"v", 0}}, 1.0)
            .add({{"brand", "shiny"},   {"category", "foo"}, {"v", 1}}, 2.0)
            .add({{"brand", "shiny"},   {"category", "foo"}, {"v", 2}}, 3.0)
            .add({{"brand", "shiny"},   {"category", "bar"}, {"v", 0}}, 1.25)
            .add({{"brand", "shiny"},   {"category", "bar"}, {"v", 1}}, 2.25)
            .add({{"brand", "shiny"},   {"category", "bar"}, {"v", 2}}, 3.25)
            .add({{"brand", "stylish"}, {"category", "bar"}, {"v", 0}}, 1.5)
            .add({{"brand", "stylish"}, {"category", "bar"}, {"v", 1}}, 2.5)
            .add({{"brand", "stylish"}, {"category", "bar"}, {"v", 2}}, 3.5)
            .add({{"brand", "stylish"}, {"category", "foo"}, {"v", 0}}, 1.75)
            .add({{"brand", "stylish"}, {"category", "foo"}, {"v", 1}}, 2.75)
            .add({{"brand", "stylish"}, {"category", "foo"}, {"v", 2}}, 3.75);
}

TEST_F(TensorLoaderTest, require_that_mixed_tensor_blocks_form_can_be_loaded)
{
    {
        SCOPED_TRACE("mixed blocks 11");
        verify_tensor(make_mixed_tensor(), f1.create(TEST_PATH("mixed-blocks-11.json"), "tensor(x{},y[2])"));
    }
    {
        SCOPED_TRACE("mixed blocks 21");
        verify_tensor(make_mix21_tensor(), f1.create(TEST_PATH("mixed-blocks-21.json"), "tensor<float>(brand{},category{},v[3])"));
    }
}

TEST_F(TensorLoaderTest, require_that_bad_lz4_file_fails_to_load_creating_empty_result)
{
    verify_tensor(sparse_tensor_nocells(), f1.create(TEST_PATH("bad_lz4.json.lz4"), "tensor(x{},y{})"));
}

void checkBitEq(double a, double b, const std::string& trace_label) {
    SCOPED_TRACE(trace_label);
    size_t aa, bb;
    memcpy(&aa, &a, sizeof(aa));
    memcpy(&bb, &b, sizeof(bb));
    EXPECT_EQ(aa, bb);
}

TEST_F(TensorLoaderTest, require_that_special_string_encoded_values_work)
{
    auto c = f1.create(TEST_PATH("dense-special.json"), "tensor<float>(z[11])");
    const auto &v = c->value();
    auto cells = v.cells().template typify<float>();
    EXPECT_EQ(std::numeric_limits<float>::infinity(), cells[0]);
    EXPECT_EQ(std::numeric_limits<float>::infinity(), cells[1]);
    EXPECT_EQ(std::numeric_limits<float>::infinity(), cells[2]);
    EXPECT_EQ(std::numeric_limits<float>::infinity(), cells[3]);
    EXPECT_EQ(-std::numeric_limits<float>::infinity(), cells[4]);
    EXPECT_EQ(-std::numeric_limits<float>::infinity(), cells[5]);
    checkBitEq(std::numeric_limits<float>::quiet_NaN(), cells[6], "6");
    checkBitEq(std::numeric_limits<float>::quiet_NaN(), cells[7], "7");
    checkBitEq(std::numeric_limits<float>::quiet_NaN(), cells[8], "8");
    checkBitEq(-std::numeric_limits<float>::quiet_NaN(), cells[9], "9");
    checkBitEq(-std::numeric_limits<float>::quiet_NaN(), cells[10], "10");
}

GTEST_MAIN_RUN_ALL_TESTS()
