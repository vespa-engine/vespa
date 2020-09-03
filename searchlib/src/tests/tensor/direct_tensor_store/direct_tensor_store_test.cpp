// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/tensor/direct_tensor_store.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/tensor.h>
#include <vespa/vespalib/datastore/datastore.hpp>

using namespace search::tensor;

using vespalib::datastore::EntryRef;
using vespalib::eval::TensorSpec;
using vespalib::tensor::DefaultTensorEngine;
using vespalib::tensor::Tensor;

vespalib::string tensor_spec("tensor(x{})");

Tensor::UP
make_tensor(const TensorSpec& spec)
{
    auto value = DefaultTensorEngine::ref().from_spec(spec);
    auto* tensor = dynamic_cast<Tensor*>(value.get());
    assert(tensor != nullptr);
    value.release();
    return Tensor::UP(tensor);
}

Tensor::UP
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

    void expect_tensor(const Tensor* exp, EntryRef ref) {
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
    size_t tensor_memory_used = store.get_tensor(ref)->count_memory_used();
    auto mem_2 = store.getMemoryUsage();
    EXPECT_GT(tensor_memory_used, 100);
    EXPECT_GE(mem_2.allocatedBytes(), mem_1.allocatedBytes() + tensor_memory_used);
    EXPECT_GT(mem_2.usedBytes(), mem_1.usedBytes() + tensor_memory_used);
}

TEST_F(DirectTensorStoreTest, invalid_ref_returns_nullptr)
{
    const auto* t = store.get_tensor(EntryRef());
    EXPECT_FALSE(t);
}

TEST_F(DirectTensorStoreTest, hold_adds_entry_to_hold_list)
{
    auto ref = store.store_tensor(make_tensor(5));
    size_t tensor_memory_used = store.get_tensor(ref)->count_memory_used();
    auto mem_1 = store.getMemoryUsage();
    store.holdTensor(ref);
    auto mem_2 = store.getMemoryUsage();
    EXPECT_GT(mem_2.allocatedBytesOnHold(), mem_1.allocatedBytesOnHold() + tensor_memory_used);
}

TEST_F(DirectTensorStoreTest, move_allocates_new_entry_and_puts_old_entry_on_hold)
{
    auto t = make_tensor(5);
    auto* exp = t.get();
    size_t tensor_memory_used = exp->count_memory_used();
    auto ref_1 = store.store_tensor(std::move(t));
    auto mem_1 = store.getMemoryUsage();

    auto ref_2 = store.move(ref_1);
    auto mem_2 = store.getMemoryUsage();
    EXPECT_NE(ref_1, ref_2);
    expect_tensor(exp, ref_1);
    expect_tensor(exp, ref_2);
    EXPECT_GT(mem_2.allocatedBytesOnHold(), mem_1.allocatedBytesOnHold() + tensor_memory_used);
}

GTEST_MAIN_RUN_ALL_TESTS()

