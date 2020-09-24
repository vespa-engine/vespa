// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/value_codec.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

using vespalib::make_string_short::fmt;

std::vector<Layout> layouts = {
    {},
    {x(3)},
    {x(3),y(5)},
    {x(3),y(5),z(7)},
    float_cells({x(3),y(5),z(7)}),
    {x({"a","b","c"})},
    {x({"a","b","c"}),y({"foo","bar"})},
    {x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y({"foo","bar"}),z({"i","j","k","l"})}),
    {x(3),y({"foo", "bar"}),z(7)},
    {x({"a","b","c"}),y(5),z({"i","j","k","l"})},
    float_cells({x({"a","b","c"}),y(5),z({"i","j","k","l"})})
};

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
    float_cells({x({"a","b","c"}),y(5)}),               float_cells({y(5),z({"i","j","k","l"})})
};

TensorSpec simple_tensor_join(const TensorSpec &a, const TensorSpec &b, join_fun_t function) {
    Stash stash;
    const auto &engine = SimpleTensorEngine::ref();
    auto lhs = engine.from_spec(a);
    auto rhs = engine.from_spec(b);
    const auto &result = engine.join(*lhs, *rhs, function, stash);
    return engine.to_spec(result);
}

TensorSpec simple_value_new_join(const TensorSpec &a, const TensorSpec &b, join_fun_t function) {
    auto lhs = value_from_spec(a, SimpleValueBuilderFactory());
    auto rhs = value_from_spec(b, SimpleValueBuilderFactory());
    auto result = new_join(*lhs, *rhs, function, SimpleValueBuilderFactory());
    return spec_from_value(*result);
}

TEST(SimpleValueTest, simple_values_can_be_converted_from_and_to_tensor_spec) {
    for (const auto &layout: layouts) {
        TensorSpec expect = spec(layout, N());
        std::unique_ptr<Value> value = value_from_spec(expect, SimpleValueBuilderFactory());
        TensorSpec actual = spec_from_value(*value);
        EXPECT_EQ(actual, expect);
    }
}

TEST(SimpleValueTest, simple_value_can_be_built_and_inspected) {
    ValueType type = ValueType::from_spec("tensor<float>(x{},y[2],z{})");
    SimpleValueBuilderFactory factory;
    std::unique_ptr<ValueBuilder<float>> builder = factory.create_value_builder<float>(type);
    float seq = 0.0;
    for (vespalib::string x: {"a", "b", "c"}) {
        for (vespalib::string y: {"aa", "bb"}) {
            auto subspace = builder->add_subspace({x, y});
            EXPECT_EQ(subspace.size(), 2);
            subspace[0] = seq + 1.0;
            subspace[1] = seq + 5.0;
            seq += 10.0;
        }
        seq += 100.0;
    }
    std::unique_ptr<Value> value = builder->build(std::move(builder));
    EXPECT_EQ(value->index().size(), 6);
    auto view = value->index().create_view({0});
    vespalib::stringref query = "b";
    vespalib::stringref label;
    size_t subspace;
    view->lookup({&query});
    EXPECT_TRUE(view->next_result({&label}, subspace));
    EXPECT_EQ(label, "aa");
    EXPECT_EQ(subspace, 2);
    EXPECT_TRUE(view->next_result({&label}, subspace));
    EXPECT_EQ(label, "bb");
    EXPECT_EQ(subspace, 3);
    EXPECT_FALSE(view->next_result({&label}, subspace));
}

TEST(SimpleValueTest, dense_join_plan_can_be_created) {
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

TEST(SimpleValueTest, sparse_join_plan_can_be_created) {
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

TEST(SimpleValueTest, dense_join_plan_can_be_executed) {
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

TEST(SimpleValueTest, new_generic_join_works_for_simple_values) {
    ASSERT_TRUE((join_layouts.size() % 2) == 0);
    for (size_t i = 0; i < join_layouts.size(); i += 2) {
        TensorSpec lhs = spec(join_layouts[i], Div16(N()));
        TensorSpec rhs = spec(join_layouts[i + 1], Div16(N()));
        for (auto fun: {operation::Add::f, operation::Sub::f, operation::Mul::f, operation::Div::f}) {
            SCOPED_TRACE(fmt("\n===\nLHS: %s\nRHS: %s\n===\n", lhs.to_string().c_str(), rhs.to_string().c_str()));
            auto expect = simple_tensor_join(lhs, rhs, fun);
            auto actual = simple_value_new_join(lhs, rhs, fun);
            EXPECT_EQ(actual, expect);
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
