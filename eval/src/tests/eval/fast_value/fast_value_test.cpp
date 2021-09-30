// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.hpp>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

using Handle = SharedStringRepo::Handle;

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

TEST(FastValueBuilderTest, scalar_add_subspace_robustness) {
    auto factory = FastValueBuilderFactory::get();
    ValueType type = ValueType::from_spec("double");
    auto builder = factory.create_value_builder<double>(type);
    auto subspace = builder->add_subspace();
    subspace[0] = 17.0;
    auto other = builder->add_subspace();
    other[0] = 42.0;
    auto value = builder->build(std::move(builder));
    EXPECT_EQ(value->index().size(), 1);
    auto actual = spec_from_value(*value);
    auto expected = TensorSpec("double").
                    add({}, 42.0);
    EXPECT_EQ(actual, expected);
}

TEST(FastValueBuilderTest, dense_add_subspace_robustness) {
    auto factory = FastValueBuilderFactory::get();
    ValueType type = ValueType::from_spec("tensor(x[2])");
    auto builder = factory.create_value_builder<double>(type);
    auto subspace = builder->add_subspace();
    subspace[0] = 17.0;
    subspace[1] = 666;
    auto other = builder->add_subspace();
    other[1] = 42.0;
    auto value = builder->build(std::move(builder));
    EXPECT_EQ(value->index().size(), 1);
    auto actual = spec_from_value(*value);
    auto expected = TensorSpec("tensor(x[2])").
                    add({{"x", 0}}, 17.0).
                    add({{"x", 1}}, 42.0);
    EXPECT_EQ(actual, expected);    
}

TEST(FastValueBuilderTest, mixed_add_subspace_robustness) {
    auto factory = FastValueBuilderFactory::get();
    ValueType type = ValueType::from_spec("tensor(x{},y[2])");
    auto builder = factory.create_value_builder<double>(type);
    auto subspace = builder->add_subspace(SA{"foo"});
    subspace[0] = 1.0;
    subspace[1] = 5.0;
    subspace = builder->add_subspace(SA{"bar"});
    subspace[0] = 2.0;
    subspace[1] = 10.0;
    auto other = builder->add_subspace(SA{"foo"});
    other[0] = 3.0;
    other[1] = 15.0;
    auto value = builder->build(std::move(builder));
    EXPECT_EQ(value->index().size(), 3);
    Handle foo("foo");
    Handle bar("bar");
    string_id label;
    string_id *label_ptr = &label;
    size_t subspace_idx;
    auto get_subspace = [&]() {
        auto cells = value->cells().typify<double>();
        return ConstArrayRef<double>(cells.begin() + subspace_idx * 2, 2);
    };
    auto view = value->index().create_view({});
    view->lookup({});
    while (view->next_result({&label_ptr, 1}, subspace_idx)) {
        if (label == bar.id()) {
            auto values = get_subspace();
            EXPECT_EQ(values[0], 2.0);
            EXPECT_EQ(values[1], 10.0);
        } else {
            EXPECT_EQ(label, foo.id());
            auto values = get_subspace();
            if (values[0] == 1) {
                EXPECT_EQ(values[1], 5.0);
            } else {
                EXPECT_EQ(values[0], 3.0);
                EXPECT_EQ(values[1], 15.0);
            }
        }
    }
}

GenSpec G() { return GenSpec(); }

const std::vector<GenSpec> layouts = {
    G(),
    G().idx("x", 3),
    G().idx("x", 3).idx("y", 5),
    G().idx("x", 3).idx("y", 5).idx("z", 7),
    G().map("x", {"a","b","c"}),
    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}),
    G().map("x", {"a","b","c"}).map("y", {"foo","bar"}).map("z", {"i","j","k","l"}),
    G().idx("x", 3).map("y", {"foo", "bar"}).idx("z", 7),
    G().map("x", {"a","b","c"}).idx("y", 5).map("z", {"i","j","k","l"})
};

TEST(FastValueBuilderFactoryTest, fast_values_can_be_copied) {
    auto factory = FastValueBuilderFactory::get();
    for (const auto &layout: layouts) {
        for (CellType ct : CellTypeUtils::list_types()) {
            auto expect = layout.cpy().cells(ct);
            if (expect.bad_scalar()) continue;
            std::unique_ptr<Value> value = value_from_spec(expect, factory);
            std::unique_ptr<Value> copy = factory.copy(*value);
            TensorSpec actual = spec_from_value(*copy);
            EXPECT_EQ(actual, expect);
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
