// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/direct_tensor_store.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/value.h>
#include <vespa/vespalib/datastore/datastore.hpp>

using namespace search::tensor;

using vespalib::datastore::EntryRef;
using vespalib::eval::SimpleValue;
using vespalib::eval::TensorSpec;
using vespalib::eval::Value;
using vespalib::eval::ValueType;
using vespalib::eval::TypedCells;
using vespalib::MemoryUsage;

vespalib::string tensor_spec("tensor(x{})");

class MockBigTensor : public Value
{
private:
    Value::UP _real_tensor;
public:
    MockBigTensor(std::unique_ptr<Value> real_tensor)
        : _real_tensor(std::move(real_tensor))
    {}
    MemoryUsage get_memory_usage() const override {
        auto memuse = _real_tensor->get_memory_usage();
        memuse.incUsedBytes(1000);
        memuse.incAllocatedBytes(1000000);
        return memuse;
    }
    const ValueType &type() const override { return _real_tensor->type(); }
    TypedCells cells() const override { return _real_tensor->cells(); }
    const Index &index() const override { return _real_tensor->index(); }
};

Value::UP
make_tensor(const TensorSpec& spec)
{
    auto value = SimpleValue::from_spec(spec);
    return std::make_unique<MockBigTensor>(std::move(value));
}

Value::UP
make_tensor(double value)
{
    return make_tensor(TensorSpec(tensor_spec).add({{"x", "a"}}, value));
}

class DirectTensorStoreTest : public ::testing::Test {
public:
    DirectTensorStore store;

    DirectTensorStoreTest() : store() {}

    virtual ~DirectTensorStoreTest() {
        store.clearHoldLists();
    }

    void expect_tensor(const Value* exp, EntryRef ref) {
        const auto* act = store.get_tensor(ref);
        ASSERT_TRUE(act);
        EXPECT_EQ(exp, act);
    }
};

TEST_F(DirectTensorStoreTest, can_set_and_get_tensor)
{
    auto t = make_tensor(5);
    auto* exp = t.get();
    auto ref = store.store_tensor(std::move(t));
    expect_tensor(exp, ref);
}

TEST_F(DirectTensorStoreTest, heap_allocated_memory_is_tracked)
{
    store.store_tensor(make_tensor(5));
    auto mem_1 = store.getMemoryUsage();
    auto ref = store.store_tensor(make_tensor(10));
    auto tensor_mem_usage = store.get_tensor(ref)->get_memory_usage();
    auto mem_2 = store.getMemoryUsage();
    EXPECT_GT(tensor_mem_usage.usedBytes(), 500);
    EXPECT_LT(tensor_mem_usage.usedBytes(), 50000);
    EXPECT_GT(tensor_mem_usage.allocatedBytes(), 500000);
    EXPECT_LT(tensor_mem_usage.allocatedBytes(), 50000000);
    EXPECT_GE(mem_2.allocatedBytes(), mem_1.allocatedBytes() + tensor_mem_usage.allocatedBytes());
    EXPECT_GT(mem_2.usedBytes(), mem_1.usedBytes() + tensor_mem_usage.allocatedBytes());
}

TEST_F(DirectTensorStoreTest, invalid_ref_returns_nullptr)
{
    const auto* t = store.get_tensor(EntryRef());
    EXPECT_FALSE(t);
}

TEST_F(DirectTensorStoreTest, hold_adds_entry_to_hold_list)
{
    auto ref = store.store_tensor(make_tensor(5));
    auto tensor_mem_usage = store.get_tensor(ref)->get_memory_usage();
    auto mem_1 = store.getMemoryUsage();
    store.holdTensor(ref);
    auto mem_2 = store.getMemoryUsage();
    EXPECT_GT(mem_2.allocatedBytesOnHold(), mem_1.allocatedBytesOnHold() + tensor_mem_usage.allocatedBytes());
}

TEST_F(DirectTensorStoreTest, move_allocates_new_entry_and_puts_old_entry_on_hold)
{
    auto t = make_tensor(5);
    auto* exp = t.get();
    auto tensor_mem_usage = exp->get_memory_usage();
    auto ref_1 = store.store_tensor(std::move(t));
    auto mem_1 = store.getMemoryUsage();

    auto ref_2 = store.move(ref_1);
    auto mem_2 = store.getMemoryUsage();
    EXPECT_NE(ref_1, ref_2);
    expect_tensor(exp, ref_1);
    expect_tensor(exp, ref_2);
    EXPECT_GT(mem_2.allocatedBytesOnHold(), mem_1.allocatedBytesOnHold() + tensor_mem_usage.allocatedBytes());
}

GTEST_MAIN_RUN_ALL_TESTS()

