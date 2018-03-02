// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("dense_dot_product_function_test");

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_xw_product_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/eval/eval/test/eval_fixture.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::tensor;
using namespace vespalib::eval::tensor_function;

const TensorEngine &prod_engine = DefaultTensorEngine::ref();

struct MyVecSeq : Sequence {
    double operator[](size_t i) const override { return (3.0 + i) * 7.0; }
};

struct MyMatSeq : Sequence {
    double operator[](size_t i) const override { return (5.0 + i) * 43.0; }
};

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("y1", spec({y(1)}, MyVecSeq()))
        .add("y3", spec({y(3)}, MyVecSeq()))
        .add("y5", spec({y(5)}, MyVecSeq()))
        .add("y16", spec({y(16)}, MyVecSeq()))
        .add("x1y1", spec({x(1),y(1)}, MyMatSeq()))
        .add("y1z1", spec({y(1),z(1)}, MyMatSeq()))
        .add("x2y3", spec({x(2),y(3)}, MyMatSeq()))
        .add("x2z3", spec({x(2),z(3)}, MyMatSeq()))
        .add("y3z2", spec({y(3),z(2)}, MyMatSeq()))
        .add("x8y5", spec({x(8),y(5)}, MyMatSeq()))
        .add("y5z8", spec({y(5),z(8)}, MyMatSeq()))
        .add("x5y16", spec({x(5),y(16)}, MyMatSeq()))
        .add("y16z5", spec({y(16),z(5)}, MyMatSeq()))
        .add("a_y3", spec({y(3)}, MyVecSeq()), "any")
        .add("y3_u", spec({y(3)}, MyVecSeq()), "tensor(y[])")
        .add("a_x2y3", spec({x(2),y(3)}, MyMatSeq()), "any")
        .add("x2_uy3", spec({x(2),y(3)}, MyMatSeq()), "tensor(x[],y[3])")
        .add("x2y3_u", spec({x(2),y(3)}, MyMatSeq()), "tensor(x[2],y[])");
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, size_t vec_size, size_t res_size, bool happy) {
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseXWProductFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQUAL(info[0]->vectorSize(), vec_size);
    EXPECT_EQUAL(info[0]->resultSize(), res_size);
    EXPECT_EQUAL(info[0]->matrixHasCommonDimensionInnermost(), happy);
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseXWProductFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that xw product gives same results as reference join/reduce") {
    // 1 -> 1 happy/unhappy
    TEST_DO(verify_optimized("reduce(y1*x1y1,sum,y)", 1, 1, true));
    TEST_DO(verify_optimized("reduce(y1*y1z1,sum,y)", 1, 1, false));
    // 3 -> 2 happy/unhappy
    TEST_DO(verify_optimized("reduce(y3*x2y3,sum,y)", 3, 2, true));
    TEST_DO(verify_optimized("reduce(y3*y3z2,sum,y)", 3, 2, false));
    // 5 -> 8 happy/unhappy
    TEST_DO(verify_optimized("reduce(y5*x8y5,sum,y)", 5, 8, true));
    TEST_DO(verify_optimized("reduce(y5*y5z8,sum,y)", 5, 8, false));
    // 16 -> 5 happy/unhappy
    TEST_DO(verify_optimized("reduce(y16*x5y16,sum,y)", 16, 5, true));
    TEST_DO(verify_optimized("reduce(y16*y16z5,sum,y)", 16, 5, false));
}

TEST("require that xw product is not optimized for abstract types") {
    TEST_DO(verify_not_optimized("reduce(a_y3*x2y3,sum)"));
    TEST_DO(verify_not_optimized("reduce(y3*a_x2y3,sum)"));
    TEST_DO(verify_not_optimized("reduce(y3_u*x2y3,sum)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2_uy3,sum)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2y3_u,sum)"));
}

TEST("require that various variants of xw product can be optimized") {
    TEST_DO(verify_optimized("reduce(y3*x2y3,sum,y)", 3, 2, true));
    TEST_DO(verify_optimized("reduce(x2y3*y3,sum,y)", 3, 2, true));
    TEST_DO(verify_optimized("reduce(join(y3,x2y3,f(x,y)(x*y)),sum,y)", 3, 2, true));
    TEST_DO(verify_optimized("reduce(join(x2y3,y3,f(x,y)(x*y)),sum,y)", 3, 2, true));
}

TEST("require that expressions similar to xw product are not optimized") {
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,sum,x)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,prod,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,sum)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(x+y)),sum,y)"));
    // TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*x)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(x*x)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*y)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*x*1)),sum,y)"));
}

TEST("require that xw products with incompatible dimensions are not optimized") {
    TEST_DO(verify_not_optimized("reduce(y3*x1y1,sum,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x8y5,sum,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2z3,sum,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2z3,sum,z)"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
