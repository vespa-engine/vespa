// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/mixed_l2_distance.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

struct FunInfo {
    using LookFor = MixedL2Distance;
    bool debug_dump;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        if (debug_dump) {
            fprintf(stderr, "%s", fun.as_string().c_str());
        }
    }
};

void verify_optimized(const vespalib::string &expr) {
    SCOPED_TRACE(expr.c_str());
    auto diff_types = CellTypeSpace(CellTypeUtils::list_types(), 2).different();
    EvalFixture::verify<FunInfo>(expr, {}, diff_types);
    auto same_types = CellTypeSpace(CellTypeUtils::list_types(), 2).same();
    EvalFixture::verify<FunInfo>(expr, {FunInfo{false}}, same_types);
}

void verify_not_optimized(const vespalib::string &expr) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace just_double({CellType::DOUBLE}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_double);
}

//-----------------------------------------------------------------------------

TEST(MixedL2DistanceTest, squared_l2_distance_can_be_optimized) {
    verify_optimized("reduce(map(x5-x5y7_2, f(a)(a * a)), sum, x)");
    verify_optimized("reduce((x5-x5y7_2)^2,sum,x)");
    verify_optimized("reduce((x5y7_2-x5)^2,sum,x)");    
    verify_optimized("sqrt(reduce(map(x5-x5y7_2, f(a)(a * a)), sum, x))");
}

TEST(MixedL2DistanceTest, trivial_dimensions_are_ignored) {
    verify_optimized("reduce((x5z1-x5y7_2)^2,sum,x)");
    verify_optimized("reduce((x5-x5y7_2z1)^2,sum,x)");
    verify_optimized("reduce((x5z1-x5y7_2z1)^2,sum,x)");
}

TEST(MixedL2DistanceTest, multiple_dimensions_can_be_used) {
    verify_optimized("reduce((x5z3-x5y7_2z3)^2,sum,x,z)");
    verify_optimized("reduce((x5-x5y7_2z3_1)^2,sum,x)");
}

TEST(MixedL2DistanceTest, not_optimizing_close_match) {
    verify_not_optimized("reduce(map(x5-x5y7_2, f(a)(a * a)), avg, x)");
    verify_not_optimized("reduce(map(x5-x5y7_2, f(a)(a + a)), sum, x)");    
}

TEST(MixedL2DistanceTest, result_must_be_sparse) {
    verify_not_optimized("reduce((x5-x5y7_2)^2,sum,x,y)");    
    verify_not_optimized("reduce((x5z1-x5y7_2)^2,sum,x,y)");
    verify_not_optimized("reduce((x5z3-x5y7_2z3)^2,sum,x)");
    verify_not_optimized("reduce((x5z3-x5y7_2z3)^2,sum,z)");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
