// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/instruction/sparse_dot_product_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();
const ValueBuilderFactory &test_factory = SimpleValueBuilderFactory::get();

//-----------------------------------------------------------------------------

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add_variants("v1_x", GenSpec(3.0).map("x", 32, 1))
        .add_variants("v2_x", GenSpec(7.0).map("x", 16, 2))
        .add("v3_y",   GenSpec().map("y", 10, 1))
        .add("v4_xd",  GenSpec().idx("x", 10))
        .add("m1_xy",  GenSpec(3.0).map("x", 32, 1).map("y", 16, 2))
        .add("m2_xy",  GenSpec(7.0).map("x", 16, 2).map("y", 32, 1))
        .add("m3_xym", GenSpec().map("x", 8, 1).idx("y", 5));
}
EvalFixture::ParamRepo param_repo = make_params();

void assert_optimized(const vespalib::string &expr) {
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EvalFixture test_fixture(test_factory, expr, param_repo, true);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(test_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<SparseDotProductFunction>().size(), 1u);
    EXPECT_EQ(test_fixture.find_all<SparseDotProductFunction>().size(), 1u);
    EXPECT_EQ(slow_fixture.find_all<SparseDotProductFunction>().size(), 0u);
}

void assert_not_optimized(const vespalib::string &expr) {
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<SparseDotProductFunction>().size(), 0u);
}

//-----------------------------------------------------------------------------

TEST(SparseDotProduct, expression_can_be_optimized)
{
    assert_optimized("reduce(v1_x*v2_x,sum,x)");
    assert_optimized("reduce(v2_x*v1_x,sum)");
    assert_optimized("reduce(v1_x_f*v2_x_f,sum)");
}

TEST(SparseDotProduct, multi_dimensional_expression_can_be_optimized)
{
    assert_optimized("reduce(m1_xy*m2_xy,sum,x,y)");
    assert_optimized("reduce(m1_xy*m2_xy,sum)");
}

TEST(SparseDotProduct, embedded_dot_product_is_not_optimized)
{
    assert_not_optimized("reduce(m1_xy*v1_x,sum,x)");
    assert_not_optimized("reduce(v1_x*m1_xy,sum,x)");
}

TEST(SparseDotProduct, similar_expressions_are_not_optimized)
{
    assert_not_optimized("reduce(m1_xy*v1_x,sum)");
    assert_not_optimized("reduce(v1_x*v3_y,sum)");    
    assert_not_optimized("reduce(v2_x*v1_x,max)");
    assert_not_optimized("reduce(v2_x+v1_x,sum)");
    assert_not_optimized("reduce(v4_xd*v4_xd,sum)");
    assert_not_optimized("reduce(m3_xym*m3_xym,sum)");
}

TEST(SparseDotProduct, mixed_cell_types_are_not_optimized)
{
    assert_not_optimized("reduce(v1_x*v2_x_f,sum)");
    assert_not_optimized("reduce(v1_x_f*v2_x,sum)");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
