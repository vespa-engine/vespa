// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/dense_dot_product_function.h>
#include <vespa/vespalib/gtest/gtest.h>
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
    std::string expr = "reduce(a*b,sum,x)";
    EvalFixture evaluator(prod_factory, expr, param_repo, true);
    EXPECT_EQ(GenSpec(wanted).gen(), evaluator.result());
    EXPECT_EQ(evaluator.result(), EvalFixture::ref(expr, param_repo));
    auto info = evaluator.find_all<DenseDotProductFunction>();
    EXPECT_EQ(info.size(), 1u);
};

TEST(DenseDotProductFunctionTest, require_that_basic_dot_product_with_equal_sizes_is_correct)
{
    check_gen_with_result(2, 2, (3.0 * 5.0) + (4.0 * 6.0));
}

//-----------------------------------------------------------------------------

void assertDotProduct(size_t numCells) {
    SCOPED_TRACE(numCells);
    check_gen_with_result(numCells, numCells, calcDotProduct(numCells));
}

void assertDotProduct(size_t lhsNumCells, size_t rhsNumCells) {
    SCOPED_TRACE(make_string("assertDotProduct(%zu,%zu)", lhsNumCells, rhsNumCells));
    size_t numCells = std::min(lhsNumCells, rhsNumCells);
    check_gen_with_result(lhsNumCells, rhsNumCells, calcDotProduct(numCells));
}

TEST(DenseDotProductFunctionTest, require_that_dot_product_with_equal_sizes_is_correct)
{
    assertDotProduct(8);
    assertDotProduct(16);
    assertDotProduct(32);
    assertDotProduct(64);
    assertDotProduct(128);
    assertDotProduct(256);
    assertDotProduct(512);
    assertDotProduct(1024);

    assertDotProduct(8 + 3);
    assertDotProduct(16 + 3);
    assertDotProduct(32 + 3);
    assertDotProduct(64 + 3);
    assertDotProduct(128 + 3);
    assertDotProduct(256 + 3);
    assertDotProduct(512 + 3);
    assertDotProduct(1024 + 3);
}

//-----------------------------------------------------------------------------

struct FunInfo {
    using LookFor = DenseDotProductFunction;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
    }
};


void assertOptimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    auto all_types = CellTypeSpace(CellTypeUtils::list_types(), 2);
    EvalFixture::verify<FunInfo>(expr, {FunInfo{}}, all_types);

}

void assertNotOptimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    CellTypeSpace just_double({CellType::DOUBLE}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_double);
}

TEST(DenseDotProductFunctionTest, require_that_dot_product_works_with_tensor_function)
{
    assertOptimized("reduce(x5$1*x5$2,sum)");
    assertOptimized("reduce(x5$1*x5$2,sum,x)");
    assertOptimized("reduce(join(x5$1,x5$2,f(x,y)(x*y)),sum)");
    assertOptimized("reduce(join(x5$1,x5$2,f(x,y)(x*y)),sum,x)");
}

TEST(DenseDotProductFunctionTest, require_that_dot_product_with_compatible_dimensions_is_optimized)
{
    assertOptimized("reduce(x1$1*x1$2,sum)");
    assertOptimized("reduce(x3$1*x3$2,sum)");
    assertOptimized("reduce(x5$1*x5$2,sum)");
}

TEST(DenseDotProductFunctionTest, require_that_dot_product_with_incompatible_dimensions_is_NOT_optimized)
{
    assertNotOptimized("reduce(x3*y3,sum)");
    assertNotOptimized("reduce(y3*x3,sum)");
    assertNotOptimized("reduce(x3*x3y3,sum)");
    assertNotOptimized("reduce(x3y3*x3,sum)");
}

TEST(DenseDotProductFunctionTest, require_that_expressions_similar_to_dot_product_are_not_optimized)
{
    assertNotOptimized("reduce(x3$1*x3$2,prod)");
    assertNotOptimized("reduce(x3$1+x3$2,sum)");
    assertNotOptimized("reduce(join(x3$1,x3$2,f(x,y)(x+y)),sum)");
    assertNotOptimized("reduce(join(x3$1,x3$2,f(x,y)(x*x)),sum)");
    assertNotOptimized("reduce(join(x3$1,x3$2,f(x,y)(y*y)),sum)");
}

TEST(DenseDotProductFunctionTest, require_that_multi_dimensional_dot_product_can_be_optimized)
{
    assertOptimized("reduce(x3y3$1*x3y3$2,sum)");
    assertOptimized("reduce(x3y3$1*x3y3$2,sum)");
}

TEST(DenseDotProductFunctionTest, require_that_result_must_be_double_to_trigger_optimization)
{
    assertOptimized("reduce(x3y3$1*x3y3$2,sum,x,y)");
    assertNotOptimized("reduce(x3y3$1*x3y3$2,sum,x)");
    assertNotOptimized("reduce(x3y3$1*x3y3$2,sum,y)");
}

void verify_compatible(const std::string &a, const std::string &b) {
    SCOPED_TRACE(make_string("verify_compatible(\"%s\",\"%s\")", a.c_str(), b.c_str()));
    auto a_type = ValueType::from_spec(a);
    auto b_type = ValueType::from_spec(b);
    EXPECT_TRUE(!a_type.is_error());
    EXPECT_TRUE(!b_type.is_error());
    EXPECT_TRUE(DenseDotProductFunction::compatible_types(ValueType::double_type(), a_type, b_type));
    EXPECT_TRUE(DenseDotProductFunction::compatible_types(ValueType::double_type(), b_type, a_type));
}

void verify_not_compatible(const std::string &a, const std::string &b) {
    SCOPED_TRACE(make_string("verify_not_compatible(\"%s\",\"%s\")", a.c_str(), b.c_str()));
    auto a_type = ValueType::from_spec(a);
    auto b_type = ValueType::from_spec(b);
    EXPECT_TRUE(!a_type.is_error());
    EXPECT_TRUE(!b_type.is_error());
    EXPECT_TRUE(!DenseDotProductFunction::compatible_types(ValueType::double_type(), a_type, b_type));
    EXPECT_TRUE(!DenseDotProductFunction::compatible_types(ValueType::double_type(), b_type, a_type));
}

TEST(DenseDotProductFunctionTest, require_that_type_compatibility_test_is_appropriate)
{
    verify_compatible("tensor(x[5])", "tensor(x[5])");
    verify_compatible("tensor(x[5])", "tensor<float>(x[5])");
    verify_compatible("tensor<float>(x[5])", "tensor(x[5])");
    verify_compatible("tensor<float>(x[5])", "tensor<float>(x[5])");
    verify_not_compatible("tensor(x[5])", "tensor(x[6])");
    verify_not_compatible("tensor(x[5])", "tensor(y[5])");
    verify_compatible("tensor(x[3],y[7],z[9])", "tensor(x[3],y[7],z[9])");
    verify_not_compatible("tensor(x[3],y[7],z[9])", "tensor(x[5],y[7],z[9])");
    verify_not_compatible("tensor(x[9],y[7],z[5])", "tensor(x[5],y[7],z[9])");
}

TEST(DenseDotProductFunctionTest, require_that_optimization_also_works_for_tensors_with_non_double_cells)
{
    assertOptimized("reduce(x5$1*x5$2,sum)");
    assertOptimized("reduce(x5$1*x5$2,sum)");
    assertOptimized("reduce(x5$1*x5$2,sum)");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
