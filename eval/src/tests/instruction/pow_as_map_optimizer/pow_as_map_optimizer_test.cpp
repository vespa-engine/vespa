// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/inplace_map_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval::operation;
using namespace vespalib::eval::tensor_function;
using namespace vespalib::eval::test;
using namespace vespalib::eval;

struct MapInfo {
    using LookFor = tensor_function::Map;
    op1_t op;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQ(fun.function(), op);
    }
};
struct InplaceInfo {
    using LookFor = InplaceMapFunction;
    op1_t op;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_TRUE(fun.inplace());
        EXPECT_EQ(fun.function(), op);
    }
};

void verify_optimized(const vespalib::string &expr, op1_t op1, bool inplace = false) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace stable_types(CellTypeUtils::list_stable_types(), 1);
    if (inplace) {
        InplaceInfo details{op1};
        EvalFixture::verify<InplaceInfo>(expr, {details}, stable_types);
    } else {
        MapInfo details{op1};
        EvalFixture::verify<MapInfo>(expr, {details}, stable_types);
    }
    MapInfo details{op1};
    CellTypeSpace unstable_types(CellTypeUtils::list_unstable_types(), 1);
    EvalFixture::verify<MapInfo>(expr, {details}, unstable_types);
}

void verify_not_optimized(const vespalib::string &expr) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace just_double({CellType::DOUBLE}, 1);
    EvalFixture::verify<MapInfo>(expr, {}, just_double);
}

TEST(PowAsMapTest, squared_dense_tensor_is_optimized) {
    verify_optimized("x5y3^2.0", Square::f);
    verify_optimized("pow(x5y3,2.0)", Square::f);
    verify_optimized("join(x5y3,2.0,f(x,y)(x^y))", Square::f);
    verify_optimized("join(x5y3,2.0,f(x,y)(pow(x,y)))", Square::f);
    verify_optimized("join(@x5y3,2.0,f(x,y)(pow(x,y)))", Square::f, true);
}

TEST(PowAsMapTest, cubed_dense_tensor_is_optimized) {
    verify_optimized("x5y3^3.0", Cube::f);
    verify_optimized("pow(x5y3,3.0)", Cube::f);
    verify_optimized("join(x5y3,3.0,f(x,y)(x^y))", Cube::f);
    verify_optimized("join(x5y3,3.0,f(x,y)(pow(x,y)))", Cube::f);
    verify_optimized("join(@x5y3,3.0,f(x,y)(pow(x,y)))", Cube::f, true);
}

TEST(PowAsMapTest, hypercubed_dense_tensor_is_not_optimized) {
    verify_not_optimized("join(x5y3,4.0,f(x,y)(pow(x,y)))");
}

TEST(PowAsMapTest, scalar_join_is_optimized) {
    vespalib::string expr = "join(@$1,2.0,f(x,y)(pow(x,y)))";
    SCOPED_TRACE(expr.c_str());
    MapInfo details{Square::f};
    CellTypeSpace just_double({CellType::DOUBLE}, 1);
    EvalFixture::verify<MapInfo>(expr, {details}, just_double);
}

TEST(PowAsMapTest, sparse_join_is_optimized) {
    verify_optimized("join(x2_1,2.0,f(x,y)(pow(x,y)))", Square::f);
}

TEST(PowAsMapTest, mixed_join_is_optimized) {
    verify_optimized("join(x1_1y5,2.0,f(x,y)(pow(x,y)))", Square::f);
}

GTEST_MAIN_RUN_ALL_TESTS()
