// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/mixed_weighted_sum.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

struct FunInfo {
    using LookFor = MixedWeightedSumFunction;
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
    CellTypeSpace just_float({CellType::FLOAT}, 2);
    CellTypeSpace just_double({CellType::DOUBLE}, 2);
    EvalFixture::verify<FunInfo>(expr, {FunInfo{false}}, just_float);
    EvalFixture::verify<FunInfo>(expr, {FunInfo{false}}, just_double);
    auto diff_types = CellTypeSpace(CellTypeUtils::list_types(), 2).different();
    EvalFixture::verify<FunInfo>(expr, {}, diff_types);
    CellTypeSpace just_bf16({CellType::BFLOAT16}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_bf16);
}

void verify_not_optimized(const vespalib::string &expr) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace just_double({CellType::DOUBLE}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_double);
}

//-----------------------------------------------------------------------------

TEST(MixedWeightedSumTest, weighted_sum_can_be_optimized) {
    verify_optimized("reduce(join(x1_1, a7_1x9_1z8, f(a, b)(a * b)), sum, x)");
    verify_optimized("reduce(join(x1_1, x9_1y1_1z8, f(a, b)(a * b)), sum, x)");
    verify_optimized("reduce(join(x1_1, x9_1y7_1z8, f(a, b)(a * b)), sum, x)");
    verify_optimized("reduce(join(x0_1, a7_1x9_1z8, f(a, b)(a * b)), sum, x)");
    verify_optimized("reduce(join(x9_1y7_1z8, x1_1, f(a, b)(a * b)), sum, x)");
    verify_optimized("reduce(join(x19_3, x19_2y7_1z8, f(a, b)(a * b)), sum, x)");
    verify_optimized("reduce(join(x1_1, a3b3x9_1y1_1, f(a, b)(a * b)), sum, x)");
    verify_optimized("reduce(join(a1_1, a7_1b7_1c7_1x8, f(a, b)(a * b)), sum, a)");
    verify_optimized("reduce(join(b1_1, a7_1b7_1c7_1x8, f(a, b)(a * b)), sum, b)");
    verify_optimized("reduce(join(c1_1, a7_1b7_1c7_1x8, f(a, b)(a * b)), sum, c)");
}

TEST(MixedWeightedSumTest, not_optimizing_close_match) {
    // optimized by MappedLookup:
    verify_not_optimized("reduce(join(x1_1, x9_1z8, f(a, b)(a * b)), sum, x)");
    // dense subspace too small:
    verify_not_optimized("reduce(join(x1_1, x9_1z7, f(a, b)(a * b)), sum, x)");
    // reducing wrong dimension:
    verify_not_optimized("reduce(join(x1_1, x9_2y7_1z8, f(a, b)(a * b)), sum, y)");
    // dimension not common:
    verify_not_optimized("reduce(join(x1_1, y7_1z8, f(a, b)(a * b)), sum, x)");
    // selector has wrong dimension:
    verify_not_optimized("reduce(join(y1_1, x9_2y7_1z8, f(a, b)(a * b)), sum, x)");
    // selector has multiple dimensions:
    verify_not_optimized("reduce(join(x1_1y1_1, x9_2y7_1z8, f(a, b)(a * b)), sum, x)");
}

TEST(MixedWeightedSumTest, result_must_have_same_dense_subspace) {
    // reducing wrong dimension:
    verify_not_optimized("reduce(join(x1_1, x9_2y7_1z8, f(a, b)(a * b)), sum, z)");
    // reducing dense dimension also:
    verify_not_optimized("reduce(join(x1_1, x9_2y7_1z8, f(a, b)(a * b)), sum, x, z)");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
