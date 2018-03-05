// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_dot_product_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

#include <vespa/log/log.h>
LOG_SETUP("dense_dot_product_function_test");

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::tensor;

const TensorEngine &prod_engine = DefaultTensorEngine::ref();

struct MyVecSeq : Sequence {
    double bias;
    double operator[](size_t i) const override { return (i + bias); }
    MyVecSeq(double cellBias) : bias(cellBias) {}
};

TensorSpec makeTensor(size_t numCells, double cellBias) {
    return spec({x(numCells)}, MyVecSeq(cellBias));
}

const double leftBias = 3.0;
const double rightBias = 5.0;

double calcDotProduct(size_t numCells) {
    double result = 0;
    for (size_t i = 0; i < numCells; ++i) {
        result += (i + leftBias) * (i + rightBias);
    }
    return result;
}

void check_gen_with_result(size_t l, size_t r, double wanted) {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("a", makeTensor(l, leftBias));
    param_repo.add("b", makeTensor(r, rightBias));
    vespalib::string expr = "reduce(a*b,sum,x)";
    EvalFixture evaluator(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(spec(wanted), evaluator.result());
    EXPECT_EQUAL(evaluator.result(), EvalFixture::ref(expr, param_repo));
    auto info = evaluator.find_all<DenseDotProductFunction>();
    EXPECT_EQUAL(info.size(), 1u);
};

// this should not be possible to set up:
// TEST("require that empty dot product is correct")

TEST("require that basic dot product with equal sizes is correct") {
    check_gen_with_result(2, 2, (3.0 * 5.0) + (4.0 * 6.0));
}

TEST("require that basic dot product with un-equal sizes is correct") {
    check_gen_with_result(2, 3, (3.0 * 5.0) + (4.0 * 6.0));
    check_gen_with_result(3, 2, (3.0 * 5.0) + (4.0 * 6.0));
}

//-----------------------------------------------------------------------------

void assertDotProduct(size_t numCells) {
    check_gen_with_result(numCells, numCells, calcDotProduct(numCells));
}

void assertDotProduct(size_t lhsNumCells, size_t rhsNumCells) {
    size_t numCells = std::min(lhsNumCells, rhsNumCells);
    check_gen_with_result(lhsNumCells, rhsNumCells, calcDotProduct(numCells));
}

TEST("require that dot product with equal sizes is correct") {
    TEST_DO(assertDotProduct(8));
    TEST_DO(assertDotProduct(16));
    TEST_DO(assertDotProduct(32));
    TEST_DO(assertDotProduct(64));
    TEST_DO(assertDotProduct(128));
    TEST_DO(assertDotProduct(256));
    TEST_DO(assertDotProduct(512));
    TEST_DO(assertDotProduct(1024));

    TEST_DO(assertDotProduct(8 + 3));
    TEST_DO(assertDotProduct(16 + 3));
    TEST_DO(assertDotProduct(32 + 3));
    TEST_DO(assertDotProduct(64 + 3));
    TEST_DO(assertDotProduct(128 + 3));
    TEST_DO(assertDotProduct(256 + 3));
    TEST_DO(assertDotProduct(512 + 3));
    TEST_DO(assertDotProduct(1024 + 3));
}

TEST("require that dot product with un-equal sizes is correct") {
    TEST_DO(assertDotProduct(8, 8 + 3));
    TEST_DO(assertDotProduct(8 + 3, 8));
    TEST_DO(assertDotProduct(16, 16 + 3));
    TEST_DO(assertDotProduct(32, 32 + 3));
    TEST_DO(assertDotProduct(64, 64 + 3));
    TEST_DO(assertDotProduct(128, 128 + 3));
    TEST_DO(assertDotProduct(256, 256 + 3));
    TEST_DO(assertDotProduct(512, 512 + 3));
    TEST_DO(assertDotProduct(1024, 1024 + 3));
}

//-----------------------------------------------------------------------------

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("v01_x1", spec({x(1)}, MyVecSeq(2.0)))
        .add("v02_x3", spec({x(3)}, MyVecSeq(4.0)))
        .add("v03_x3", spec({x(3)}, MyVecSeq(5.0)))
        .add("v04_y3", spec({y(3)}, MyVecSeq(10)))
        .add("v05_x5", spec({x(5)}, MyVecSeq(6.0)))
        .add("v06_x5", spec({x(5)}, MyVecSeq(7.0)))
        .add("v07_x3_a", spec({x(3)}, MyVecSeq(8.0)), "any")
        .add("v08_x3_u", spec({x(3)}, MyVecSeq(9.0)), "tensor(x[])")
        .add("v09_x4_u", spec({x(4)}, MyVecSeq(3.0)), "tensor(x[])")
        .add("m01_x3y3", spec({x(3),y(3)}, MyVecSeq(0)));
}
EvalFixture::ParamRepo param_repo = make_params();

void assertOptimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseDotProductFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
}

void assertNotOptimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseDotProductFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that dot product is not optimized for unknown types") {
    TEST_DO(assertNotOptimized("reduce(v02_x3*v07_x3_a,sum)"));
    TEST_DO(assertNotOptimized("reduce(v07_x3_a*v03_x3,sum)"));
}

TEST("require that dot product works with tensor function") {
    TEST_DO(assertOptimized("reduce(v05_x5*v06_x5,sum)"));
    TEST_DO(assertOptimized("reduce(v05_x5*v06_x5,sum,x)"));
    TEST_DO(assertOptimized("reduce(join(v05_x5,v06_x5,f(x,y)(x*y)),sum)"));
    TEST_DO(assertOptimized("reduce(join(v05_x5,v06_x5,f(x,y)(x*y)),sum,x)"));
}

TEST("require that dot product with compatible dimensions is optimized") {
    TEST_DO(assertOptimized("reduce(v01_x1*v01_x1,sum)"));
    TEST_DO(assertOptimized("reduce(v02_x3*v03_x3,sum)"));
    TEST_DO(assertOptimized("reduce(v05_x5*v06_x5,sum)"));

    TEST_DO(assertOptimized("reduce(v02_x3*v06_x5,sum)"));
    TEST_DO(assertOptimized("reduce(v05_x5*v03_x3,sum)"));
    TEST_DO(assertOptimized("reduce(v08_x3_u*v05_x5,sum)"));
    TEST_DO(assertOptimized("reduce(v05_x5*v08_x3_u,sum)"));
}

TEST("require that dot product with incompatible dimensions is NOT optimized") {
    TEST_DO(assertNotOptimized("reduce(v02_x3*v04_y3,sum)"));
    TEST_DO(assertNotOptimized("reduce(v04_y3*v02_x3,sum)"));
    TEST_DO(assertNotOptimized("reduce(v08_x3_u*v04_y3,sum)"));
    TEST_DO(assertNotOptimized("reduce(v04_y3*v08_x3_u,sum)"));
    TEST_DO(assertNotOptimized("reduce(v02_x3*m01_x3y3,sum)"));
    TEST_DO(assertNotOptimized("reduce(m01_x3y3*v02_x3,sum)"));
}

TEST("require that expressions similar to dot product are not optimized") {
    TEST_DO(assertNotOptimized("reduce(v02_x3*v03_x3,prod)"));
    TEST_DO(assertNotOptimized("reduce(v02_x3+v03_x3,sum)"));
    TEST_DO(assertNotOptimized("reduce(join(v02_x3,v03_x3,f(x,y)(x+y)),sum)"));
    TEST_DO(assertNotOptimized("reduce(join(v02_x3,v03_x3,f(x,y)(x*x)),sum)"));
    TEST_DO(assertNotOptimized("reduce(join(v02_x3,v03_x3,f(x,y)(y*y)),sum)"));
    // TEST_DO(assertNotOptimized("reduce(join(v02_x3,v03_x3,f(x,y)(y*x)),sum)"));
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
