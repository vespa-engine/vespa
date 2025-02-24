// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/replace_type_function.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("x1y5z1", GenSpec().idx("x", 1).idx("y", 5).idx("z", 1))
        .add("x1y5z1f", GenSpec().idx("x", 1).idx("y", 5).idx("z", 1).cells_float())
        .add("x1y1z1", GenSpec().idx("x", 1).idx("y", 1).idx("z", 1))
        .add("x1y5z_m", GenSpec().idx("x", 1).idx("y", 5).map("z", {"a"}));
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

TEST(RemoveTrivialDimensionOptimizerTest, require_that_dimension_removal_can_be_optimized_for_appropriate_aggregators)
{
    verify_optimized("reduce(x1y5z1,avg,x)");
    verify_not_optimized("reduce(x1y5z1,count,x)"); // NB
    verify_optimized("reduce(x1y5z1,prod,x)");
    verify_optimized("reduce(x1y5z1,sum,x)");
    verify_optimized("reduce(x1y5z1,max,x)");
    verify_optimized("reduce(x1y5z1,min,x)");
}

TEST(RemoveTrivialDimensionOptimizerTest, require_that_multi_dimension_removal_can_be_optimized)
{
    verify_optimized("reduce(x1y5z1,sum,x,z)");
}

TEST(RemoveTrivialDimensionOptimizerTest, require_that_chained_dimension_removal_can_be_optimized_and_compacted)
{
    verify_optimized("reduce(reduce(x1y5z1,sum,x),sum,z)");
}

TEST(RemoveTrivialDimensionOptimizerTest, require_that_reducing_non_trivial_dimension_is_not_optimized)
{
    verify_not_optimized("reduce(x1y5z1,sum,y)");
    verify_not_optimized("reduce(x1y5z1,sum,x,y)");
    verify_not_optimized("reduce(x1y5z1,sum,y,z)");
}

TEST(RemoveTrivialDimensionOptimizerTest, require_that_full_reduce_is_not_optimized)
{
    verify_not_optimized("reduce(x1y1z1,sum)");
    verify_not_optimized("reduce(x1y1z1,sum,x,y,z)");
}

TEST(RemoveTrivialDimensionOptimizerTest, require_that_mixed_tensor_types_can_be_optimized)
{
    verify_optimized("reduce(x1y5z_m,sum,x)");
    verify_not_optimized("reduce(x1y5z_m,sum,y)");
    verify_not_optimized("reduce(x1y5z_m,sum,z)");
}

TEST(RemoveTrivialDimensionOptimizerTest, require_that_optimization_works_for_float_cells)
{
    verify_optimized("reduce(x1y5z1f,avg,x)");
}

GTEST_MAIN_RUN_ALL_TESTS()
