// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/dense_matmul_function.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

struct FunInfo {
    using LookFor = DenseMatMulFunction;
    size_t lhs_size;
    size_t common_size;
    size_t rhs_size;
    bool lhs_inner;
    bool rhs_inner;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQ(fun.lhs_size(), lhs_size);
        EXPECT_EQ(fun.common_size(), common_size);
        EXPECT_EQ(fun.rhs_size(), rhs_size);
        EXPECT_EQ(fun.lhs_common_inner(), lhs_inner);
        EXPECT_EQ(fun.rhs_common_inner(), rhs_inner);
    }
};

void verify_optimized(const std::string &expr, FunInfo details) {
    SCOPED_TRACE(expr);
    CellTypeSpace all_types(CellTypeUtils::list_types(), 2);
    EvalFixture::verify<FunInfo>(expr, {details}, all_types);
}

void verify_not_optimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    CellTypeSpace just_double({CellType::DOUBLE}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_double);
}

TEST(DenseMatMulFunctionTest, require_that_matmul_can_be_optimized)
{
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .lhs_inner = true, .rhs_inner = true };
    verify_optimized("reduce(a2d3*b5d3,sum,d)", details);
}

TEST(DenseMatMulFunctionTest, require_that_matmul_with_lambda_can_be_optimized)
{
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .lhs_inner = true, .rhs_inner = true };
    verify_optimized("reduce(join(a2d3,b5d3,f(x,y)(x*y)),sum,d)", details);
}

TEST(DenseMatMulFunctionTest, require_that_expressions_similar_to_matmul_are_not_optimized)
{
    verify_not_optimized("reduce(a2d3*b5d3,sum,a)");
    verify_not_optimized("reduce(a2d3*b5d3,sum,b)");
    verify_not_optimized("reduce(a2d3*b5d3,prod,d)");
    verify_not_optimized("reduce(a2d3*b5d3,sum)");
    verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(y*x)),sum,d)");
    verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(x+y)),sum,d)");
    verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(x*x)),sum,d)");
    verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(y*y)),sum,d)");
    verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(x*y*1)),sum,d)");
    verify_not_optimized("reduce(a2c3*b5d3,sum,d)");
    verify_not_optimized("reduce(a2c3*b5d3,sum,c)");
}

TEST(DenseMatMulFunctionTest, require_that_MatMul_can_be_debug_dumped)
{
    EvalFixture fixture(prod_factory, "reduce(x*y,sum,d)", EvalFixture::ParamRepo()
                        .add("x", GenSpec::from_desc("a2d3"))
                        .add("y", GenSpec::from_desc("b5d3")), true);
    auto info = fixture.find_all<DenseMatMulFunction>();
    ASSERT_EQ(info.size(), 1u);
    fprintf(stderr, "%s\n", info[0]->as_string().c_str());
}

TEST(DenseMatMulFunctionTest, require_that_matmul_inner_inner_works_correctly)
{
    FunInfo details = { .lhs_size = 2, .common_size = 3, .rhs_size = 5,
                        .lhs_inner = true, .rhs_inner = true };
    verify_optimized("reduce(a2d3*b5d3,sum,d)", details);
    verify_optimized("reduce(b5d3*a2d3,sum,d)", details);
}

TEST(DenseMatMulFunctionTest, require_that_matmul_inner_outer_works_correctly)
{
    FunInfo details = { .lhs_size = 2, .common_size = 5, .rhs_size = 3,
                        .lhs_inner = true, .rhs_inner = false };
    verify_optimized("reduce(a2b5*b5d3,sum,b)", details);
    verify_optimized("reduce(b5d3*a2b5,sum,b)", details);
}

TEST(DenseMatMulFunctionTest, require_that_matmul_outer_outer_works_correctly)
{
    FunInfo details = { .lhs_size = 2, .common_size = 5, .rhs_size = 3,
                        .lhs_inner = false, .rhs_inner = false };
    verify_optimized("reduce(b5c2*b5d3,sum,b)", details);
    verify_optimized("reduce(b5d3*b5c2,sum,b)", details);
}

GTEST_MAIN_RUN_ALL_TESTS()
