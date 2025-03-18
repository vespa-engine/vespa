// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/dense_tensor_store.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>
#include <vespa/eval/eval/test/value_compare.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
LOG_SETUP("dense_tensor_store_test");

using search::tensor::DenseTensorStore;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;

using EntryRef = DenseTensorStore::EntryRef;

Value::UP
makeTensor(const TensorSpec &spec)
{
    return SimpleValue::from_spec(spec);
}

struct Fixture
{
    DenseTensorStore store;
    explicit Fixture(const std::string &tensorType)
        : store(ValueType::from_spec(tensorType), {})
    {}
    void assertSetAndGetTensor(const TensorSpec &tensorSpec) {
        Value::UP expTensor = makeTensor(tensorSpec);
        EntryRef ref = store.store_tensor(*expTensor);
        Value::UP actTensor = store.get_tensor(ref);
        EXPECT_EQ(*expTensor, *actTensor);
        assertTensorView(ref, *expTensor);
    }
    void assertEmptyTensor(const TensorSpec &tensorSpec) const {
        Value::UP expTensor = makeTensor(tensorSpec);
        EntryRef ref;
        Value::UP actTensor = store.get_tensor(ref);
        EXPECT_TRUE(actTensor.get() == nullptr);
        assertTensorView(ref, *expTensor);
    }
    void assertTensorView(EntryRef ref, const Value &expTensor) const {
        auto cells = store.get_typed_cells(ref);
        vespalib::eval::DenseValueView actTensor(store.type(), cells);
        EXPECT_EQ(expTensor, actTensor);
    }
};

TEST(DenseTensorStoreTest, require_that_we_can_store_1d_bound_tensor)
{
    Fixture f("tensor(x[3])");
    f.assertSetAndGetTensor(TensorSpec("tensor(x[3])").
                                       add({{"x", 0}}, 2).
                                       add({{"x", 1}}, 3).
                                       add({{"x", 2}}, 5));
}

TEST(DenseTensorStoreTest, require_that_correct_empty_tensor_is_returned_for_1d_bound_tensor)
{
    Fixture f("tensor(x[3])");
    f.assertEmptyTensor(TensorSpec("tensor(x[3])").
                                   add({{"x", 0}}, 0).
                                   add({{"x", 1}}, 0).
                                   add({{"x", 2}}, 0));
}

size_t array_size(const std::string&tensorType) {
    Fixture f(tensorType);
    return f.store.getArraySize();
}

TEST(DenseTensorStoreTest, require_that_array_size_is_calculated_correctly)
{
    EXPECT_EQ(8, array_size("tensor(x[1])"));
    EXPECT_EQ(96, array_size("tensor(x[10])"));
    EXPECT_EQ(32, array_size("tensor(x[3])"));
    EXPECT_EQ(800, array_size("tensor(x[10],y[10])"));
    EXPECT_EQ(8, array_size("tensor<int8>(x[1])"));
    EXPECT_EQ(8, array_size("tensor<int8>(x[8])"));
    EXPECT_EQ(16, array_size("tensor<int8>(x[9])"));
    EXPECT_EQ(16, array_size("tensor<int8>(x[16])"));
    EXPECT_EQ(32, array_size("tensor<int8>(x[17])"));
    EXPECT_EQ(32, array_size("tensor<int8>(x[32])"));
    EXPECT_EQ(64, array_size("tensor<int8>(x[33])"));
    EXPECT_EQ(64, array_size("tensor<int8>(x[64])"));
    EXPECT_EQ(96, array_size("tensor<int8>(x[65])"));
}

uint32_t max_buffer_entries(const std::string& tensor_type) {
    Fixture f(tensor_type);
    return f.store.get_max_buffer_entries();
}

TEST(DenseTensorStoreTest, require_that_max_entries_is_calculated_correctly)
{
    EXPECT_EQ(1_Mi, max_buffer_entries("tensor(x[1])"));
    EXPECT_EQ(1_Mi, max_buffer_entries("tensor(x[32])"));
    EXPECT_EQ(512_Ki, max_buffer_entries("tensor(x[64])"));
    EXPECT_EQ(32_Ki, max_buffer_entries("tensor(x[1024])"));
    EXPECT_EQ(32_Ki, max_buffer_entries("tensor(x[1024])"));
    EXPECT_EQ(2, max_buffer_entries("tensor(x[16777216])"));
    EXPECT_EQ(2, max_buffer_entries("tensor(x[33554428])"));
    EXPECT_EQ(1, max_buffer_entries("tensor(x[33554429])"));
    EXPECT_EQ(1, max_buffer_entries("tensor(x[33554432])"));
}

GTEST_MAIN_RUN_ALL_TESTS()
