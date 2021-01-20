// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
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

struct MyVecSeq : Sequence {
    double bias;
    double operator[](size_t i) const override { return (i + bias); }
    MyVecSeq(double cellBias) : bias(cellBias) {}
};

std::function<double(size_t)> my_vec_gen(double cellBias) {
    return [=] (size_t i) { return i + cellBias; };
}

//-----------------------------------------------------------------------------

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add_vector("x", 3, my_vec_gen(2.0))
        .add_vector("x", 3, my_vec_gen(13.25))
        .add_vector("y", 3, my_vec_gen(4.0))
        .add_vector("z", 3, my_vec_gen(0.25))
        .add_matrix("x", 3, "y", 1, my_vec_gen(5.0))
        .add_matrix("x", 1, "y", 3, my_vec_gen(6.0))
        .add_matrix("x", 3, "y", 3, my_vec_gen(1.5))
        .add_matrix("x", 3, "z", 3, my_vec_gen(2.5))
        .add_cube("x", 3, "y", 3, "z", 3, my_vec_gen(-4.0))
        .add("mix_x3zm", spec({x(3),z({"c","d"})}, MyVecSeq(0.5)))
        .add("mix_y3zm", spec({y(3),z({"c","d"})}, MyVecSeq(3.5)))
        .add("mix_x3zm_f", spec(float_cells({x(3),z({"c","d"})}), MyVecSeq(0.5)))
        .add("mix_y3zm_f", spec(float_cells({y(3),z({"c","d"})}), MyVecSeq(3.5)))
        .add("mix_x3y3zm", spec({x(3),y(3),z({"c","d"})}, MyVecSeq(0.0)))
        ;

}
EvalFixture::ParamRepo param_repo = make_params();

void assert_mixed_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<MixedInnerProductFunction>();
    ASSERT_EQ(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
}

void assert_not_mixed_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<MixedInnerProductFunction>();
    ASSERT_EQ(info.size(), 0u);
}

void assert_dense_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<MixedInnerProductFunction>();
    ASSERT_EQ(info.size(), 0u);
    auto info2 = fixture.find_all<DenseDotProductFunction>();
    auto info3 = fixture.find_all<DenseMatMulFunction>();
    auto info4 = fixture.find_all<DenseMultiMatMulFunction>();
    auto info5 = fixture.find_all<DenseXWProductFunction>();
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
    assert_mixed_optimized("reduce(x3f * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(x3 * mix_x3zm_f,sum,x)");
    assert_mixed_optimized("reduce(x3f * mix_x3zm_f,sum,x)");
    assert_mixed_optimized("reduce(x3$2 * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(x3f$2 * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(y3 * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(y3f * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(y3 * mix_y3zm_f,sum,y)");
    assert_mixed_optimized("reduce(y3f * mix_y3zm_f,sum,y)");
    assert_mixed_optimized("reduce(x3y1 * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(x3y1f * mix_x3zm,sum,x)");
    assert_mixed_optimized("reduce(x3y1 * mix_x3zm,sum,x,y)");
    assert_mixed_optimized("reduce(x3y1f * mix_x3zm,sum,x,y)");
    assert_mixed_optimized("reduce(x1y3 * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(x1y3f * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(x1y3 * x1y3,sum,y)");
    assert_mixed_optimized("reduce(x1y3 * x1y3f,sum,y)");
    assert_mixed_optimized("reduce(x1y3f * x1y3,sum,y)");
    assert_mixed_optimized("reduce(x1y3f * x1y3f,sum,y)");
    assert_mixed_optimized("reduce(x1y3 * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(x1y3f * mix_y3zm,sum,y)");
    assert_mixed_optimized("reduce(mix_x3zm * x3,sum,x)");
    assert_mixed_optimized("reduce(mix_x3zm * x3f,sum,x)");
    assert_mixed_optimized("reduce(mix_x3zm * x3y1,sum,x)");
    assert_mixed_optimized("reduce(mix_x3zm * x3y1f,sum,x)");
    assert_mixed_optimized("reduce(mix_y3zm * y3,sum,y)");
    assert_mixed_optimized("reduce(mix_y3zm * y3f,sum,y)");
    assert_mixed_optimized("reduce(mix_y3zm * x1y3,sum,y)");
    assert_mixed_optimized("reduce(mix_y3zm * x1y3f,sum,y)");
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
