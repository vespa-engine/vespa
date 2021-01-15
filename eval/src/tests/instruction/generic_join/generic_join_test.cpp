// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/instruction/generic_join.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/reference_operations.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<Layout> join_layouts = {
    {},                                                 {},
    {x(5)},                                             {x(5)},
    {x(5)},                                             {y(5)},
    {x(5)},                                             {x(5),y(5)},
    {y(3)},                                             {x(2),z(3)},
    {x(3),y(5)},                                        {y(5),z(7)},
    float_cells({x(3),y(5)}),                           {y(5),z(7)},
    {x(3),y(5)},                                        float_cells({y(5),z(7)}),
    float_cells({x(3),y(5)}),                           float_cells({y(5),z(7)}),
    {x({"a","b","c"})},                                 {x({"a","b","c"})},
    {x({"a","b","c"})},                                 {x({"a","b"})},
    {x({"a","b","c"})},                                 {y({"foo","bar","baz"})},
    {x({"a","b","c"})},                                 {x({"a","b","c"}),y({"foo","bar","baz"})},
    {x({"a","b"}),y({"foo","bar","baz"})},              {x({"a","b","c"}),y({"foo","bar"})},
    {x({"a","b"}),y({"foo","bar","baz"})},              {y({"foo","bar"}),z({"i","j","k","l"})},
    float_cells({x({"a","b"}),y({"foo","bar","baz"})}), {y({"foo","bar"}),z({"i","j","k","l"})},
    {x({"a","b"}),y({"foo","bar","baz"})},              float_cells({y({"foo","bar"}),z({"i","j","k","l"})}),
    float_cells({x({"a","b"}),y({"foo","bar","baz"})}), float_cells({y({"foo","bar"}),z({"i","j","k","l"})}),
    {x(3),y({"foo", "bar"})},                           {y({"foo", "bar"}),z(7)},
    {x({"a","b","c"}),y(5)},                            {y(5),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y(5)}),               {y(5),z({"i","j","k","l"})},
    {x({"a","b","c"}),y(5)},                            float_cells({y(5),z({"i","j","k","l"})}),
    float_cells({x({"a","b","c"}),y(5)}),               float_cells({y(5),z({"i","j","k","l"})}),
    {x({"a","b","c"}),y(5)},                            float_cells({y(5)}),
    {y(5)},                                             float_cells({x({"a","b","c"}),y(5)}),
    {x({}),y(5)},                                       float_cells({y(5)})
};

bool join_address(const TensorSpec::Address &a, const TensorSpec::Address &b, TensorSpec::Address &addr) {
    for (const auto &dim_a: a) {
        auto pos_b = b.find(dim_a.first);
        if ((pos_b != b.end()) && !(pos_b->second == dim_a.second)) {
            return false;
        }
        addr.insert_or_assign(dim_a.first, dim_a.second);
    }
    return true;
}

TensorSpec perform_generic_join(const TensorSpec &a, const TensorSpec &b,
                                join_fun_t function, const ValueBuilderFactory &factory)
{
    Stash stash;
    auto lhs = value_from_spec(a, factory);
    auto rhs = value_from_spec(b, factory);
    auto my_op = GenericJoin::make_instruction(lhs->type(), rhs->type(), function, factory, stash);
    InterpretedFunction::EvalSingle single(factory, my_op);
    return spec_from_value(single.eval(std::vector<Value::CREF>({*lhs,*rhs})));
}

TEST(GenericJoinTest, dense_join_plan_can_be_created) {
    auto lhs = ValueType::from_spec("tensor(a{},b[6],c[5],e[3],f[2],g{})");
    auto rhs = ValueType::from_spec("tensor(a{},b[6],c[5],d[4],h{})");
    auto plan = DenseJoinPlan(lhs, rhs);
    std::vector<size_t> expect_loop = {30,4,6};
    std::vector<size_t> expect_lhs_stride = {6,0,1};
    std::vector<size_t> expect_rhs_stride = {4,1,0};
    EXPECT_EQ(plan.lhs_size, 180);
    EXPECT_EQ(plan.rhs_size, 120);
    EXPECT_EQ(plan.out_size, 720);
    EXPECT_EQ(plan.loop_cnt, expect_loop);
    EXPECT_EQ(plan.lhs_stride, expect_lhs_stride);
    EXPECT_EQ(plan.rhs_stride, expect_rhs_stride);
}

TEST(GenericJoinTest, sparse_join_plan_can_be_created) {
    auto lhs = ValueType::from_spec("tensor(a{},b[6],c[5],e[3],f[2],g{})");
    auto rhs = ValueType::from_spec("tensor(b[6],c[5],d[4],g{},h{})");
    auto plan = SparseJoinPlan(lhs, rhs);
    using SRC = SparseJoinPlan::Source;
    std::vector<SRC> expect_sources = {SRC::LHS,SRC::BOTH,SRC::RHS};
    std::vector<size_t> expect_lhs_overlap = {1};
    std::vector<size_t> expect_rhs_overlap = {0};
    EXPECT_EQ(plan.sources, expect_sources);
    EXPECT_EQ(plan.lhs_overlap, expect_lhs_overlap);
    EXPECT_EQ(plan.rhs_overlap, expect_rhs_overlap);
}

TEST(GenericJoinTest, dense_join_plan_can_be_executed) {
    auto plan = DenseJoinPlan(ValueType::from_spec("tensor(a[2])"),
                              ValueType::from_spec("tensor(b[3])"));
    std::vector<int> a({1, 2});
    std::vector<int> b({3, 4, 5});
    std::vector<int> c(6, 0);
    std::vector<int> expect = {3,4,5,6,8,10};
    ASSERT_EQ(plan.out_size, 6);
    int *dst = &c[0];
    auto cell_join = [&](size_t a_idx, size_t b_idx) { *dst++ = (a[a_idx] * b[b_idx]); };
    plan.execute(0, 0, cell_join);
    EXPECT_EQ(c, expect);
}

TEST(GenericJoinTest, generic_join_works_for_simple_and_fast_values) {
    ASSERT_TRUE((join_layouts.size() % 2) == 0);
    for (size_t i = 0; i < join_layouts.size(); i += 2) {
        TensorSpec lhs = spec(join_layouts[i], Div16(N()));
        TensorSpec rhs = spec(join_layouts[i + 1], Div16(N()));
        for (auto fun: {operation::Add::f, operation::Sub::f, operation::Mul::f, operation::Div::f}) {
            SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
            auto expect = ReferenceOperations::join(lhs, rhs, fun);
            auto simple = perform_generic_join(lhs, rhs, fun, SimpleValueBuilderFactory::get());
            auto fast = perform_generic_join(lhs, rhs, fun, FastValueBuilderFactory::get());
            EXPECT_EQ(simple, expect);
            EXPECT_EQ(fast, expect);
        }
    }
}


GTEST_MAIN_RUN_ALL_TESTS()
