// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/replace_type_function.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("x5", GenSpec().idx("x", 5))
        .add("x5f", GenSpec().idx("x", 5).cells_float())
        .add("x5y1", GenSpec().idx("x", 5).idx("y", 1))
        .add("y1z1", GenSpec().idx("y", 5).idx("z", 1))
        .add("x_m", GenSpec().map("x", {"a"}));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<ReplaceTypeFunction>();
    EXPECT_EQ(info.size(), 1u);
}

void verify_not_optimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<ReplaceTypeFunction>();
    EXPECT_TRUE(info.empty());
}

TEST(AddTrivialDimensionOptimizerTest, require_that_dimension_addition_can_be_optimized)
{
    verify_optimized("join(x5,tensor(y[1])(1),f(a,b)(a*b))");
    verify_optimized("join(tensor(y[1])(1),x5,f(a,b)(a*b))");
    verify_optimized("x5*tensor(y[1])(1)");
    verify_optimized("tensor(y[1])(1)*x5");
    verify_optimized("x5y1*tensor(z[1])(1)");
    verify_optimized("tensor(z[1])(1)*x5y1");
}

TEST(AddTrivialDimensionOptimizerTest, require_that_multi_dimension_addition_can_be_optimized)
{
    verify_optimized("x5*tensor(a[1],b[1],c[1])(1)");
}

TEST(AddTrivialDimensionOptimizerTest, require_that_dimension_addition_can_be_chained_and_compacted)
{
    verify_optimized("tensor(z[1])(1)*x5*tensor(y[1])(1)");
}

TEST(AddTrivialDimensionOptimizerTest, require_that_constant_dimension_addition_is_optimized)
{
    verify_optimized("tensor(x[1])(1)*tensor(y[1])(1)");
    verify_optimized("tensor(x[1])(1.1)*tensor(y[1])(1)");
    verify_optimized("tensor(x[1])(1)*tensor(y[1])(1.1)");
    verify_optimized("tensor(x[2])(1)*tensor(y[1])(1)");
    verify_optimized("tensor(x[1])(1)*tensor(y[2])(1)");
}

TEST(AddTrivialDimensionOptimizerTest, require_that_non_canonical_dimension_addition_is_not_optimized)
{
    verify_not_optimized("x5+tensor(y[1])(0)");
    verify_not_optimized("tensor(y[1])(0)+x5");
    verify_not_optimized("x5-tensor(y[1])(0)");
    verify_not_optimized("x5/tensor(y[1])(1)");
    verify_not_optimized("tensor(y[1])(1)/x5");
}

TEST(AddTrivialDimensionOptimizerTest, require_that_dimension_addition_with_overlapping_dimensions_is_optimized)
{
    verify_optimized("x5y1*tensor(y[1],z[1])(1)");
    verify_optimized("tensor(y[1],z[1])(1)*x5y1");
}

TEST(AddTrivialDimensionOptimizerTest, require_that_dimension_addition_with_mixed_dimensions_is_optimized)
{
    verify_optimized("x_m*tensor(y[1])(1)");
    verify_optimized("tensor(y[1])(1)*x_m");
}

TEST(AddTrivialDimensionOptimizerTest, require_that_dimension_addition_optimization_requires_unit_constant_tensor)
{
    verify_not_optimized("x5*tensor(y[1])(0.9)");
    verify_not_optimized("tensor(y[1])(1.1)*x5");
    verify_not_optimized("x5*tensor(y[1],z[2])(1)");
    verify_not_optimized("tensor(y[1],z[2])(1)*x5");
    verify_not_optimized("x5*y1z1");
    verify_not_optimized("y1z1*x5");
    verify_not_optimized("tensor(x[1])(1.1)*tensor(y[1])(1.1)");
    verify_not_optimized("tensor(x[2])(1)*tensor(y[2])(1)");
}

TEST(AddTrivialDimensionOptimizerTest, require_that_optimization_also_works_for_float_cells)
{
    verify_optimized("x5*tensor<float>(a[1],b[1],c[1])(1)");
    verify_optimized("x5f*tensor<float>(a[1],b[1],c[1])(1)");
}

TEST(AddTrivialDimensionOptimizerTest, require_that_optimization_is_disabled_if_unit_vector_would_promote_tensor_cell_types)
{
    verify_not_optimized("x5f*tensor(a[1],b[1],c[1])(1)");
}

GTEST_MAIN_RUN_ALL_TESTS()
