// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/dense_dot_product_function.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/util/stringfmt.h>

#include <vespa/log/log.h>
LOG_SETUP("dense_dot_product_function_test");

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

TensorSpec makeTensor(size_t numCells, double cellBias) {
    return GenSpec(cellBias).idx("x", numCells);
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
    EvalFixture evaluator(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(GenSpec(wanted).gen(), evaluator.result());
    EXPECT_EQUAL(evaluator.result(), EvalFixture::ref(expr, param_repo));
    auto info = evaluator.find_all<DenseDotProductFunction>();
    EXPECT_EQUAL(info.size(), 1u);
};

// this should not be possible to set up:
// TEST("require that empty dot product is correct")

TEST("require that basic dot product with equal sizes is correct") {
    check_gen_with_result(2, 2, (3.0 * 5.0) + (4.0 * 6.0));
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

//-----------------------------------------------------------------------------

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("v01_x1", GenSpec(2.0).idx("x", 1))
        .add("v02_x3", GenSpec(4.0).idx("x", 3))
        .add("v03_x3", GenSpec(5.0).idx("x", 3))
        .add("v04_y3", GenSpec(10).idx("y", 3))
        .add("v05_x5", GenSpec(6.0).idx("x", 5))
        .add("v06_x5", GenSpec(7.0).idx("x", 5))
        .add("v07_x5f", GenSpec(7.0).cells_float().idx("x", 5))
        .add("v08_x5f", GenSpec(6.0).cells_float().idx("x", 5))
        .add("m01_x3y3", GenSpec(1.0).idx("x", 3).idx("y", 3))
        .add("m02_x3y3", GenSpec(2.0).idx("x", 3).idx("y", 3));
}
EvalFixture::ParamRepo param_repo = make_params();

void assertOptimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseDotProductFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
}

void assertNotOptimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseDotProductFunction>();
    EXPECT_TRUE(info.empty());
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
}

TEST("require that dot product with incompatible dimensions is NOT optimized") {
    TEST_DO(assertNotOptimized("reduce(v02_x3*v04_y3,sum)"));
    TEST_DO(assertNotOptimized("reduce(v04_y3*v02_x3,sum)"));
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

TEST("require that multi-dimensional dot product can be optimized") {
    TEST_DO(assertOptimized("reduce(m01_x3y3*m02_x3y3,sum)"));
    TEST_DO(assertOptimized("reduce(m02_x3y3*m01_x3y3,sum)"));
}

TEST("require that result must be double to trigger optimization") {
    TEST_DO(assertOptimized("reduce(m01_x3y3*m01_x3y3,sum,x,y)"));
    TEST_DO(assertNotOptimized("reduce(m01_x3y3*m01_x3y3,sum,x)"));
    TEST_DO(assertNotOptimized("reduce(m01_x3y3*m01_x3y3,sum,y)"));
}

void verify_compatible(const vespalib::string &a, const vespalib::string &b) {
    auto a_type = ValueType::from_spec(a);
    auto b_type = ValueType::from_spec(b);
    EXPECT_TRUE(!a_type.is_error());
    EXPECT_TRUE(!b_type.is_error());
    EXPECT_TRUE(DenseDotProductFunction::compatible_types(ValueType::double_type(), a_type, b_type));
    EXPECT_TRUE(DenseDotProductFunction::compatible_types(ValueType::double_type(), b_type, a_type));
}

void verify_not_compatible(const vespalib::string &a, const vespalib::string &b) {
    auto a_type = ValueType::from_spec(a);
    auto b_type = ValueType::from_spec(b);
    EXPECT_TRUE(!a_type.is_error());
    EXPECT_TRUE(!b_type.is_error());
    EXPECT_TRUE(!DenseDotProductFunction::compatible_types(ValueType::double_type(), a_type, b_type));
    EXPECT_TRUE(!DenseDotProductFunction::compatible_types(ValueType::double_type(), b_type, a_type));
}

TEST("require that type compatibility test is appropriate") {
    TEST_DO(verify_compatible("tensor(x[5])", "tensor(x[5])"));
    TEST_DO(verify_compatible("tensor(x[5])", "tensor<float>(x[5])"));
    TEST_DO(verify_compatible("tensor<float>(x[5])", "tensor(x[5])"));
    TEST_DO(verify_compatible("tensor<float>(x[5])", "tensor<float>(x[5])"));
    TEST_DO(verify_not_compatible("tensor(x[5])", "tensor(x[6])"));
    TEST_DO(verify_not_compatible("tensor(x[5])", "tensor(y[5])"));
    TEST_DO(verify_compatible("tensor(x[3],y[7],z[9])", "tensor(x[3],y[7],z[9])"));
    TEST_DO(verify_not_compatible("tensor(x[3],y[7],z[9])", "tensor(x[5],y[7],z[9])"));
    TEST_DO(verify_not_compatible("tensor(x[9],y[7],z[5])", "tensor(x[5],y[7],z[9])"));
}

TEST("require that optimization also works for tensors with non-double cells") {
    TEST_DO(assertOptimized("reduce(v05_x5*v07_x5f,sum)"));
    TEST_DO(assertOptimized("reduce(v07_x5f*v05_x5,sum)"));
    TEST_DO(assertOptimized("reduce(v07_x5f*v08_x5f,sum)"));
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
