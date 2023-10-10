// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/dense_dot_product_function.h>
#include <vespa/eval/instruction/dense_matmul_function.h>
#include <vespa/eval/instruction/dense_multi_matmul_function.h>
#include <vespa/eval/instruction/dense_xw_product_function.h>
#include <vespa/eval/instruction/mixed_inner_product_function.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("mixed_inner_product_function_test");

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

//-----------------------------------------------------------------------------

template <typename T>
struct FunInfo {
    using LookFor = T;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
    }
};

void assert_mixed_optimized(const vespalib::string &expr) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace all_types(CellTypeUtils::list_types(), 2);
    using MIP = FunInfo<MixedInnerProductFunction>;
    EvalFixture::verify<MIP>(expr, {MIP{}}, all_types);
}

void assert_not_mixed_optimized(const vespalib::string &expr) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace all_types(CellTypeUtils::list_types(), 2);
    using MIP = FunInfo<MixedInnerProductFunction>;
    EvalFixture::verify<MIP>(expr, {}, all_types);
}

void assert_dense_optimized(const vespalib::string &expr) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace all_types(CellTypeUtils::list_types(), 2);
    using MIP = FunInfo<MixedInnerProductFunction>;
    EvalFixture::verify<MIP>(expr, {}, all_types);
    using XWP = FunInfo<DenseXWProductFunction>;
    EvalFixture::verify<XWP>(expr, {XWP{}}, all_types);
}

//-----------------------------------------------------------------------------

TEST(MixedInnerProduct, use_dense_optimizers_when_possible) {
    // actually, all these trigger DenseXWProduct (prioritized before MixedInner)
    assert_dense_optimized("reduce(x3 * x3y1,sum,x)");
    assert_dense_optimized("reduce(y3 * x1y3,sum,y)");
    assert_dense_optimized("reduce(y3 * x3y3,sum,y)");
    assert_dense_optimized("reduce(x1y3 * y3,sum,y)");
    assert_dense_optimized("reduce(x3y3 * y3,sum,y)");
}

TEST(MixedInnerProduct, trigger_optimizer_when_possible) {
    assert_mixed_optimized("reduce(x3 * x3z2_1,sum,x)");
    assert_mixed_optimized("reduce(x3$2 * x3z2_1,sum,x)");
    assert_mixed_optimized("reduce(y3 * y3z2_1,sum,y)");
    assert_mixed_optimized("reduce(x3y1 * x3z2_1,sum,x)");
    assert_mixed_optimized("reduce(x3y1 * x3z2_1,sum,x,y)");
    assert_mixed_optimized("reduce(x1y3 * y3z2_1,sum,y)");
    assert_mixed_optimized("reduce(x1y3 * x1y3$2,sum,y)");
    assert_mixed_optimized("reduce(x1y3 * y3z2_1,sum,y)");
    assert_mixed_optimized("reduce(x3z2_1 * x3,sum,x)");
    assert_mixed_optimized("reduce(x3z2_1 * x3y1,sum,x)");
    assert_mixed_optimized("reduce(y3z2_1 * y3,sum,y)");
    assert_mixed_optimized("reduce(y3z2_1 * x1y3,sum,y)");
}

TEST(MixedInnerProduct, should_not_trigger_optimizer_for_other_cases) {
    assert_not_mixed_optimized("reduce(x3y3z3 * x3,sum,x)");
    assert_not_mixed_optimized("reduce(x3y3z3 * y3,sum,y)");
    assert_not_mixed_optimized("reduce(x3y3z3 * x3y3,sum,x,y)");
    assert_not_mixed_optimized("reduce(x3y3 * y3z2_1,sum,y)");
    assert_not_mixed_optimized("reduce(y3z2_1 * x3,sum,x,y)");
    assert_not_mixed_optimized("reduce(x3y3z2_1 * y3,sum,y,z)");
    assert_not_mixed_optimized("reduce(x3y3z2_1 * y3,sum,x,y)");
}

TEST(MixedInnerProduct, check_compatibility_with_complex_types) {
    ValueType vec_type = ValueType::from_spec("tensor<float>(f[1],g[2],i[1],x[3],y[1])");
    ValueType mix_type = ValueType::from_spec("tensor<double>(cat{},g[2],host{},k[1],x[3],z{})");
    ValueType res_type = ValueType::join(vec_type,mix_type).reduce({"g","k","i","x"});
    EXPECT_EQ(MixedInnerProductFunction::compatible_types(res_type, mix_type, vec_type), true);
    EXPECT_EQ(MixedInnerProductFunction::compatible_types(res_type, vec_type, mix_type), false);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
