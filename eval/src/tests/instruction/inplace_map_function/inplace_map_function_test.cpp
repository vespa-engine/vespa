// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/inplace_map_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

struct FunInfo {
    using LookFor = InplaceMapFunction;
    bool debug_dump;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQ(fun.inplace(), true);
        if (debug_dump) {
            fprintf(stderr, "%s", fun.as_string().c_str());
        }
        REQUIRE(fun.result_is_mutable() && fun.inplace());
    }
};

void verify_optimized(const vespalib::string &expr) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace stable_types(CellTypeUtils::list_stable_types(), 1);
    CellTypeSpace unstable_types(CellTypeUtils::list_unstable_types(), 1);
    EvalFixture::verify<FunInfo>(expr, {FunInfo{false}}, stable_types);
    EvalFixture::verify<FunInfo>(expr, {}, unstable_types);
}

void verify_not_optimized(const vespalib::string &expr) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace just_double({CellType::DOUBLE}, 1);
    EvalFixture::verify<FunInfo>(expr, {}, just_double);
}

TEST(MapTest, dense_map_can_be_optimized) {
    verify_not_optimized("map(x5y3,f(x)(x+10))");
    verify_optimized("map(@x5y3,f(x)(x+10))");
}

TEST(MapTest, scalar_map_is_not_optimized) {
    verify_not_optimized("map(@$1,f(x)(x+10))");
}

TEST(MapTest, sparse_map_can_be_optimized) {
    verify_not_optimized("map(x1_1,f(x)(x+10))");
    verify_optimized("map(@x1_1,f(x)(x+10))");
}

TEST(MapTest, mixed_map_can_be_optimized) {
    verify_not_optimized("map(y1_1z2,f(x)(x+10))");
    verify_optimized("map(@y1_1z2,f(x)(x+10))");
}

TEST(MapTest, mixed_map_can_be_debug_dumped) {
    CellTypeSpace just_double({CellType::DOUBLE}, 1);
    EvalFixture::verify<FunInfo>("map(@y1_1z2,f(x)(x+10))", {FunInfo{true}}, just_double);
}

GTEST_MAIN_RUN_ALL_TESTS()
