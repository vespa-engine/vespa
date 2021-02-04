// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
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

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();


//-----------------------------------------------------------------------------

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add_variants("x3", GenSpec(2.0).idx("x", 3))
        .add_variants("x3$2", GenSpec(13.25).idx("x", 3))
        .add_variants("y3", GenSpec(4.0).idx("y", 3))
        .add_variants("z3", GenSpec(0.25).idx("z", 3))
        .add_variants("x3y3", GenSpec(5.0).idx("x", 3).idx("y", 3))
        .add_variants("x1y3", GenSpec(6.0).idx("x", 1).idx("y", 3))
        .add_variants("x3y1", GenSpec(1.5).idx("x", 3).idx("y", 1))
        .add_variants("x3z3", GenSpec(2.5).idx("x", 3).idx("z", 3))
        .add_variants("x3y3z3", GenSpec(-4.0).idx("x", 3).idx("y", 3).idx("z", 3))
        .add("mix_x3zm", GenSpec(0.5).idx("x", 3).map("z", {"c","d"}))
        .add("mix_y3zm", GenSpec(3.5).idx("y", 3).map("z", {"c","d"}))
        .add("mix_x3zm_f", GenSpec(0.5).idx("x", 3).map("z", {"c","d"}).cells_float())
        .add("mix_y3zm_f", GenSpec(3.5).idx("y", 3).map("z", {"c","d"}).cells_float())
        .add("mix_x3y3zm", GenSpec(0.0).idx("x", 3).idx("y", 3).map("z", {"c","d"}))
        ;

}
EvalFixture::ParamRepo param_repo = make_params();

void assert_mixed_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fast_fixture.find_all<MixedInnerProductFunction>();
    ASSERT_EQ(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
}

void assert_not_mixed_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fast_fixture.find_all<MixedInnerProductFunction>();
    ASSERT_EQ(info.size(), 0u);
}

void assert_dense_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fast_fixture.find_all<MixedInnerProductFunction>();
    ASSERT_EQ(info.size(), 0u);
    auto info2 = fast_fixture.find_all<DenseDotProductFunction>();
    auto info3 = fast_fixture.find_all<DenseMatMulFunction>();
    auto info4 = fast_fixture.find_all<DenseMultiMatMulFunction>();
    auto info5 = fast_fixture.find_all<DenseXWProductFunction>();
    ASSERT_EQ(info2.size() + info3.size() + info4.size() + info5.size(), 1u);
}

//-----------------------------------------------------------------------------

TEST(MixedInnerProduct, use_dense_optimizers_when_possible) {
    // actually, all these trigger DenseXWProduct
    assert_dense_optimized("reduce(x3 * x3y1,sum,x)");
    assert_dense_optimized("reduce(y3 * x1y3,sum,y)");
    assert_dense_optimized("reduce(y3 * x3y3,sum,y)");
    assert_dense_optimized("reduce(x1y3 * y3,sum,y)");
    assert_dense_optimized("reduce(x3y3 * y3,sum,y)");
}

TEST(MixedInnerProduct, trigger_optimizer_when_possible) {
    assert_mixed_optimized("reduce(x3 * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(x3_f * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(x3 * mix_x3zm_f,sum,x)");
    assert_mixed_optimized("reduce(x3_f * mix_x3zm_f,sum,x)");
    assert_mixed_optimized("reduce(x3$2 * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(x3$2_f * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(y3 * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(y3_f * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(y3 * mix_y3zm_f,sum,y)");
    assert_mixed_optimized("reduce(y3_f * mix_y3zm_f,sum,y)");
    assert_mixed_optimized("reduce(x3y1 * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(x3y1_f * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(x3y1 * mix_x3zm,sum,x,y)");
    assert_mixed_optimized("reduce(x3y1_f * mix_x3zm,sum,x,y)");
    assert_mixed_optimized("reduce(x1y3 * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(x1y3_f * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(x1y3 * x1y3,sum,y)");
    assert_mixed_optimized("reduce(x1y3 * x1y3_f,sum,y)");
    assert_mixed_optimized("reduce(x1y3_f * x1y3,sum,y)");
    assert_mixed_optimized("reduce(x1y3_f * x1y3_f,sum,y)");
    assert_mixed_optimized("reduce(x1y3 * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(x1y3_f * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(mix_x3zm * x3,sum,x)");
    assert_mixed_optimized("reduce(mix_x3zm * x3_f,sum,x)");
    assert_mixed_optimized("reduce(mix_x3zm * x3y1,sum,x)");
    assert_mixed_optimized("reduce(mix_x3zm * x3y1_f,sum,x)");
    assert_mixed_optimized("reduce(mix_y3zm * y3,sum,y)");
    assert_mixed_optimized("reduce(mix_y3zm * y3_f,sum,y)");
    assert_mixed_optimized("reduce(mix_y3zm * x1y3,sum,y)");
    assert_mixed_optimized("reduce(mix_y3zm * x1y3_f,sum,y)");
}

TEST(MixedInnerProduct, should_not_trigger_optimizer_for_other_cases) {
    assert_not_mixed_optimized("reduce(x3y3z3 * x3,sum,x)");
    assert_not_mixed_optimized("reduce(x3y3z3 * y3,sum,y)");
    assert_not_mixed_optimized("reduce(x3y3z3 * x3y3,sum,x,y)");
    assert_not_mixed_optimized("reduce(x3y3 * mix_y3zm,sum,y)");
    assert_not_mixed_optimized("reduce(mix_y3zm * x3,sum,x,y)");
    assert_not_mixed_optimized("reduce(mix_x3y3zm * y3,sum,y,z)");
    assert_not_mixed_optimized("reduce(mix_x3y3zm * y3,sum,x,y)");
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
