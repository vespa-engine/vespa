// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_reduce.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <optional>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<Layout> layouts = {
    {},
    {x(3)},
    {x(3),y(5)},
    {x(3),y(5),z(7)},
    float_cells({x(3),y(5),z(7)}),
    {x({"a","b","c"})},
    {x({})},
    {x({}),y(10)},
    {x({"a","b","c"}),y({"foo","bar"})},
    {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}),
    {x(3),y({"foo", "bar"}),z(7)},
    {x({"a","b","c"}),y(5),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y(5),z({"i","j","k","l"})})
};

TensorSpec reference_reduce(const TensorSpec &a, const std::vector<vespalib::string> &dims, Aggr aggr) {
    Stash stash;
    ValueType res_type = ValueType::from_spec(a.type()).reduce(dims);
    EXPECT_FALSE(res_type.is_error());
    std::map<TensorSpec::Address,std::optional<Aggregator*>> my_map;
    for (const auto &cell: a.cells()) {
        TensorSpec::Address addr;
        for (const auto &dim: cell.first) {
            if (res_type.dimension_index(dim.first) != ValueType::Dimension::npos) {
                addr.insert_or_assign(dim.first, dim.second);
            }
        }
        auto [pos, is_empty] = my_map.emplace(addr, std::nullopt);
        if (is_empty) {
            pos->second = &Aggregator::create(aggr, stash);
            pos->second.value()->first(cell.second);
        } else {
            pos->second.value()->next(cell.second);
        }
    }
    TensorSpec result(res_type.to_spec());
    for (const auto &my_entry: my_map) {
        result.add(my_entry.first, my_entry.second.value()->result());
    }
    // use SimpleValue to add implicit cells with default value
    const auto &factory = SimpleValueBuilderFactory::get();
    return spec_from_value(*value_from_spec(result, factory));
}

TensorSpec perform_generic_reduce(const TensorSpec &a, const std::vector<vespalib::string> &dims, Aggr aggr) {
    Stash stash;
    const auto &factory = SimpleValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto my_op = GenericReduce::make_instruction(lhs->type(), aggr, dims, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs})));
}

TEST(GenericReduceTest, dense_reduce_plan_can_be_created) {
    auto type = ValueType::from_spec("tensor(a[2],aa{},b[2],bb[1],c[2],cc{},d[2],dd[1],e[2],ee{},f[2])");
    auto plan = DenseReducePlan(type, type.reduce({"a", "d", "e"}));
    std::vector<size_t> expect_keep_loop = {4,2};
    std::vector<size_t> expect_keep_stride = {8,1};
    std::vector<size_t> expect_reduce_loop = {2,4};
    std::vector<size_t> expect_reduce_stride = {32,2};
    EXPECT_EQ(plan.in_size, 64);
    EXPECT_EQ(plan.out_size, 8);
    EXPECT_EQ(plan.keep_loop, expect_keep_loop);
    EXPECT_EQ(plan.keep_stride, expect_keep_stride);
    EXPECT_EQ(plan.reduce_loop, expect_reduce_loop);
    EXPECT_EQ(plan.reduce_stride, expect_reduce_stride);
}

TEST(GenericReduceTest, sparse_reduce_plan_can_be_created) {
    auto type = ValueType::from_spec("tensor(a{},aa[10],b{},c{},cc[5],d{},e{},ee[1],f{})");
    auto plan = SparseReducePlan(type, type.reduce({"a", "d", "e"}));
    std::vector<size_t> expect_keep_dims = {1,2,5};
    EXPECT_EQ(plan.num_reduce_dims, 3);
    EXPECT_EQ(plan.keep_dims, expect_keep_dims);
}

TEST(GenericReduceTest, generic_reduce_works_for_simple_values) {
    for (const Layout &layout: layouts) {
        TensorSpec input = spec(layout, Div16(N()));
        for (Aggr aggr: {Aggr::SUM, Aggr::AVG, Aggr::MIN, Aggr::MAX}) {
            for (const Domain &domain: layout) {
                auto expect = reference_reduce(input, {domain.dimension}, aggr);
                auto actual = perform_generic_reduce(input, {domain.dimension}, aggr);
                EXPECT_EQ(actual, expect);
            }
            auto expect = reference_reduce(input, {}, aggr);
            auto actual = perform_generic_reduce(input, {}, aggr);
            EXPECT_EQ(actual, expect);
        }
    }
}

TensorSpec immediate_generic_reduce(const TensorSpec &a, const std::vector<vespalib::string> &dims, Aggr aggr) {
    const auto &factory = SimpleValueBuilderFactory::get();
    auto lhs = value_from_spec(a, factory);
    auto up = GenericReduce::perform_reduce(*lhs, aggr, dims, factory);
    return spec_from_value(*up);
}

TEST(GenericReduceTest, immediate_generic_reduce_works) {
    for (const Layout &layout: layouts) {
        TensorSpec input = spec(layout, Div16(N()));
        for (Aggr aggr: {Aggr::SUM, Aggr::AVG, Aggr::MIN, Aggr::MAX}) {
            for (const Domain &domain: layout) {
                auto expect = reference_reduce(input, {domain.dimension}, aggr);
                auto actual = immediate_generic_reduce(input, {domain.dimension}, aggr);
                EXPECT_EQ(actual, expect);
            }
            auto expect = reference_reduce(input, {}, aggr);
            auto actual = immediate_generic_reduce(input, {}, aggr);
            EXPECT_EQ(actual, expect);
        }
    }
}


GTEST_MAIN_RUN_ALL_TESTS()
