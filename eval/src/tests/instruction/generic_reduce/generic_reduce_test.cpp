// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_reduce.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/reference_operations.h>
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

TensorSpec perform_generic_reduce(const TensorSpec &a, const std::vector<vespalib::string> &dims,
                                  Aggr aggr, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto my_op = GenericReduce::make_instruction(lhs->type(), aggr, dims, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs})));
}

TEST(GenericReduceTest, dense_reduce_plan_can_be_created) {
    auto type = ValueType::from_spec("tensor(a[2],aa{},b[2],bb[1],c[2],cc{},d[2],dd[1],e[2],ee{},f[2])");
    auto plan = DenseReducePlan(type, type.reduce({"a", "d", "e"}));
    std::vector<size_t> expect_loop_cnt = {2,4,4,2};
    std::vector<size_t> expect_in_stride = {32,2,8,1};
    std::vector<size_t> expect_out_stride = {0,0,2,1};
    EXPECT_EQ(plan.in_size, 64);
    EXPECT_EQ(plan.out_size, 8);
    EXPECT_EQ(plan.loop_cnt, expect_loop_cnt);
    EXPECT_EQ(plan.in_stride, expect_in_stride);
    EXPECT_EQ(plan.out_stride, expect_out_stride);
}

TEST(GenericReduceTest, sparse_reduce_plan_can_be_created) {
    auto type = ValueType::from_spec("tensor(a{},aa[10],b{},c{},cc[5],d{},e{},ee[1],f{})");
    auto plan = SparseReducePlan(type, type.reduce({"a", "d", "e"}));
    std::vector<size_t> expect_keep_dims = {1,2,5};
    EXPECT_EQ(plan.num_reduce_dims, 3);
    EXPECT_EQ(plan.keep_dims, expect_keep_dims);
}

void test_generic_reduce_with(const ValueBuilderFactory &factory) {
    for (const Layout &layout: layouts) {
        TensorSpec input = spec(layout, Div16(N()));
        for (Aggr aggr: {Aggr::SUM, Aggr::AVG, Aggr::MIN, Aggr::MAX}) {
            for (const Domain &domain: layout) {
                auto expect = ReferenceOperations::reduce(input, {domain.dimension}, aggr);
                auto actual = perform_generic_reduce(input, {domain.dimension}, aggr, factory);
                EXPECT_EQ(actual, expect);
            }
            auto expect = ReferenceOperations::reduce(input, {}, aggr);
            auto actual = perform_generic_reduce(input, {}, aggr, factory);
            EXPECT_EQ(actual, expect);
        }
    }
}

TEST(GenericReduceTest, generic_reduce_works_for_simple_values) {
    test_generic_reduce_with(SimpleValueBuilderFactory::get());
}

TEST(GenericReduceTest, generic_reduce_works_for_fast_values) {
    test_generic_reduce_with(FastValueBuilderFactory::get());
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
                auto expect = ReferenceOperations::reduce(input, {domain.dimension}, aggr);
                auto actual = immediate_generic_reduce(input, {domain.dimension}, aggr);
                EXPECT_EQ(actual, expect);
            }
            auto expect = ReferenceOperations::reduce(input, {}, aggr);
            auto actual = immediate_generic_reduce(input, {}, aggr);
            EXPECT_EQ(actual, expect);
        }
    }
}


GTEST_MAIN_RUN_ALL_TESTS()
