// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/instruction/dense_join_reduce_plan.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::instruction;

ValueType type(const vespalib::string &type_spec) {
    return ValueType::from_spec(type_spec);
}

TEST(DenseJoinReducePlanTest, make_trivial_plan) {
    auto plan = DenseJoinReducePlan(type("double"), type("double"), type("double"));
    EXPECT_TRUE(plan.distinct_result());
    EXPECT_EQ(plan.lhs_size, 1);
    EXPECT_EQ(plan.rhs_size, 1);
    EXPECT_EQ(plan.res_size, 1);
    EXPECT_TRUE(plan.loop_cnt.empty());
    EXPECT_TRUE(plan.lhs_stride.empty());
    EXPECT_TRUE(plan.rhs_stride.empty());
    EXPECT_TRUE(plan.res_stride.empty());
}

TEST(DenseJoinReducePlanTest, execute_trivial_plan) {
    auto plan = DenseJoinReducePlan(type("double"), type("double"), type("double"));
    size_t res = 0;
    auto join_reduce = [&](size_t a_idx, size_t b_idx, size_t c_idx) {
                           res += (12 + a_idx + b_idx + c_idx);
                       };
    plan.execute(5, 10, 15, join_reduce);
    EXPECT_EQ(res, 42);
}

TEST(DenseJoinReducePlanTest, make_simple_plan) {
    auto plan = DenseJoinReducePlan(type("tensor(a[2])"), type("tensor(b[3])"), type("tensor(a[2])"));
    SmallVector<size_t> expect_loop = {2,3};
    SmallVector<size_t> expect_lhs_stride = {1,0};
    SmallVector<size_t> expect_rhs_stride = {0,1};
    SmallVector<size_t> expect_res_stride = {1,0};
    EXPECT_FALSE(plan.distinct_result());
    EXPECT_EQ(plan.lhs_size, 2);
    EXPECT_EQ(plan.rhs_size, 3);
    EXPECT_EQ(plan.res_size, 2);
    EXPECT_EQ(plan.loop_cnt, expect_loop);
    EXPECT_EQ(plan.lhs_stride, expect_lhs_stride);
    EXPECT_EQ(plan.rhs_stride, expect_rhs_stride);
    EXPECT_EQ(plan.res_stride, expect_res_stride);
}

TEST(DenseJoinReducePlanTest, execute_simple_plan) {
    auto plan = DenseJoinReducePlan(type("tensor(a[2])"), type("tensor(b[3])"), type("tensor(a[2])"));
    std::vector<int> a({1, 2});
    std::vector<int> b({3, 4, 5});
    std::vector<int> c(2, 0);
    std::vector<int> expect = {12, 24};
    ASSERT_EQ(plan.res_size, 2);
    auto join_reduce = [&](size_t a_idx, size_t b_idx, size_t c_idx) { c[c_idx] += (a[a_idx] * b[b_idx]); };
    plan.execute(0, 0, 0, join_reduce);
    EXPECT_EQ(c, expect);
}

TEST(DenseJoinReducePlanTest, make_distinct_plan) {
    auto plan = DenseJoinReducePlan(type("tensor(a[2])"),
                                    type("tensor(b[3])"),
                                    type("tensor(a[2],b[3])"));
    SmallVector<size_t> expect_loop = {2,3};
    SmallVector<size_t> expect_lhs_stride = {1,0};
    SmallVector<size_t> expect_rhs_stride = {0,1};
    SmallVector<size_t> expect_res_stride = {3,1};
    EXPECT_TRUE(plan.distinct_result());
    EXPECT_EQ(plan.lhs_size, 2);
    EXPECT_EQ(plan.rhs_size, 3);
    EXPECT_EQ(plan.res_size, 6);
    EXPECT_EQ(plan.loop_cnt, expect_loop);
    EXPECT_EQ(plan.lhs_stride, expect_lhs_stride);
    EXPECT_EQ(plan.rhs_stride, expect_rhs_stride);
    EXPECT_EQ(plan.res_stride, expect_res_stride);
}

TEST(DenseJoinReducePlanTest, make_complex_plan) {
    auto lhs = type("tensor(a{},b[6],c[5],e[3],f[2],g{})");
    auto rhs = type("tensor(a{},b[6],c[5],d[4],h{})");
    auto res = type("tensor(a{},b[6],c[5],d[4],e[3])");
    auto plan = DenseJoinReducePlan(lhs, rhs, res);
    SmallVector<size_t> expect_loop = {30,4,3,2};
    SmallVector<size_t> expect_lhs_stride = {6,0,2,1};
    SmallVector<size_t> expect_rhs_stride = {4,1,0,0};
    SmallVector<size_t> expect_res_stride = {12,3,1,0};
    EXPECT_FALSE(plan.distinct_result());
    EXPECT_EQ(plan.lhs_size, 180);
    EXPECT_EQ(plan.rhs_size, 120);
    EXPECT_EQ(plan.res_size, 360);
    EXPECT_EQ(plan.loop_cnt, expect_loop);
    EXPECT_EQ(plan.lhs_stride, expect_lhs_stride);
    EXPECT_EQ(plan.rhs_stride, expect_rhs_stride);
    EXPECT_EQ(plan.res_stride, expect_res_stride);
}

GTEST_MAIN_RUN_ALL_TESTS()
