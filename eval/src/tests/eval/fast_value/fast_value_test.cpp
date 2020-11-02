// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;

TEST(FastCellsTest, push_back_fast_works) {
    FastCells<float> cells(3);
    EXPECT_EQ(cells.capacity, 4);
    EXPECT_EQ(cells.size, 0);
    cells.push_back_fast(1.0);
    cells.push_back_fast(2.0);
    cells.push_back_fast(3.0);
    EXPECT_EQ(cells.capacity, 4);
    EXPECT_EQ(cells.size, 3);
    cells.ensure_free(3);
    EXPECT_EQ(cells.capacity, 8);
    EXPECT_EQ(cells.size, 3);
    cells.push_back_fast(4.0);
    cells.push_back_fast(5.0);
    cells.push_back_fast(6.0);
    EXPECT_EQ(cells.capacity, 8);
    EXPECT_EQ(cells.size, 6);
    auto usage = cells.estimate_extra_memory_usage();
    EXPECT_EQ(usage.allocatedBytes(), sizeof(float) * 8);
    EXPECT_EQ(usage.usedBytes(), sizeof(float) * 6);
    EXPECT_EQ(*cells.get(0), 1.0);
    EXPECT_EQ(*cells.get(1), 2.0);
    EXPECT_EQ(*cells.get(2), 3.0);
    EXPECT_EQ(*cells.get(3), 4.0);
    EXPECT_EQ(*cells.get(4), 5.0);
    EXPECT_EQ(*cells.get(5), 6.0);
}

TEST(FastCellsTest, add_cells_works) {
    FastCells<float> cells(3);
    auto arr1 = cells.add_cells(3);
    EXPECT_EQ(cells.capacity, 4);
    EXPECT_EQ(cells.size, 3);
    arr1[0] = 1.0;
    arr1[1] = 2.0;
    arr1[2] = 3.0;
    auto arr2 = cells.add_cells(3);
    EXPECT_EQ(cells.capacity, 8);
    EXPECT_EQ(cells.size, 6);
    arr2[0] = 4.0;
    arr2[1] = 5.0;
    arr2[2] = 6.0;
    EXPECT_EQ(*cells.get(0), 1.0);
    EXPECT_EQ(*cells.get(1), 2.0);
    EXPECT_EQ(*cells.get(2), 3.0);
    EXPECT_EQ(*cells.get(3), 4.0);
    EXPECT_EQ(*cells.get(4), 5.0);
    EXPECT_EQ(*cells.get(5), 6.0);
}

using SA = std::vector<vespalib::stringref>;

TEST(FastValueTest, add_subspace_robustness) {
    auto factory = FastValueBuilderFactory::get();
    ValueType type = ValueType::from_spec("tensor(x[2])");
    auto builder = factory.create_value_builder<double>(type);
    auto subspace = builder->add_subspace({});
    subspace[0] = 17.0;
    subspace[1] = 666;
    auto other = builder->add_subspace({});
    other[1] = 42.0;
    auto value = builder->build(std::move(builder));
    auto actual = spec_from_value(*value);
    auto expected = TensorSpec("tensor(x[2])").
                    add({{"x", 0}}, 17.0).
                    add({{"x", 1}}, 42.0);
    EXPECT_EQ(actual, expected);    

    type = ValueType::from_spec("tensor(x{})");
    builder = factory.create_value_builder<double>(type);
    subspace = builder->add_subspace(SA{"foo"});
    subspace[0] = 17.0;
    subspace = builder->add_subspace(SA{"bar"});
    subspace[0] = 18.0;
    other = builder->add_subspace(SA{"foo"});
    other[0] = 42.0;
    value = builder->build(std::move(builder));
    actual = spec_from_value(*value);
    expected = TensorSpec("tensor(x{})").
               add({{"x", "bar"}}, 18.0).
               add({{"x", "foo"}}, 42.0);
    EXPECT_EQ(actual, expected);    

    type = ValueType::from_spec("tensor(x{},y[2])");
    builder = factory.create_value_builder<double>(type);
    subspace = builder->add_subspace(SA{"foo"});
    subspace[0] = 17.0;
    subspace[1] = 666;
    subspace = builder->add_subspace(SA{"bar"});
    subspace[0] = 18.0;
    subspace[1] = 19.0;
    other = builder->add_subspace(SA{"foo"});
    other[1] = 42.0;
    value = builder->build(std::move(builder));
    actual = spec_from_value(*value);
    expected = TensorSpec("tensor(x{},y[2])").
               add({{"x", "foo"}, {"y", 0}}, 17.0).
               add({{"x", "bar"}, {"y", 0}}, 18.0).
               add({{"x", "bar"}, {"y", 1}}, 19.0).
               add({{"x", "foo"}, {"y", 1}}, 42.0);
    EXPECT_EQ(actual, expected);    
}

GTEST_MAIN_RUN_ALL_TESTS()
