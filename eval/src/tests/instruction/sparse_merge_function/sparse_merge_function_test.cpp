// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/instruction/sparse_merge_function.h>
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
        .add("scalar1", GenSpec(1.0))
        .add("scalar2", GenSpec(2.0))
        .add_variants("v1_x", GenSpec(3.0).map("x", 32, 1))
        .add_variants("v2_x", GenSpec(4.0).map("x", 16, 2))
        .add_variants("v3_xz", GenSpec(5.0).map("x", 16, 2).idx("z", 1))
        .add("dense",  GenSpec(6.0).idx("x", 10))
        .add("m1_xy",  GenSpec(7.0).map("x", 32, 1).map("y", 16, 2))
        .add("m2_xy",  GenSpec(8.0).map("x", 16, 2).map("y", 32, 1))
        .add("mixed", GenSpec(9.0).map("x", 8, 1).idx("y", 5));
}
EvalFixture::ParamRepo param_repo = make_params();

void assert_optimized(const vespalib::string &expr) {
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EvalFixture test_fixture(test_factory, expr, param_repo, true);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(test_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<SparseMergeFunction>().size(), 1u);
    EXPECT_EQ(test_fixture.find_all<SparseMergeFunction>().size(), 1u);
    EXPECT_EQ(slow_fixture.find_all<SparseMergeFunction>().size(), 0u);
}

void assert_not_optimized(const vespalib::string &expr) {
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<SparseMergeFunction>().size(), 0u);
}

//-----------------------------------------------------------------------------

TEST(SparseMerge, expression_can_be_optimized)
{
    assert_optimized("merge(v1_x,v2_x,f(x,y)(x+y))");
    assert_optimized("merge(v1_x,v2_x,f(x,y)(max(x,y)))");
    assert_optimized("merge(v1_x,v2_x,f(x,y)(x+y+1))");
    assert_optimized("merge(v1_x_f,v2_x_f,f(x,y)(x+y))");
    assert_optimized("merge(v3_xz,v3_xz,f(x,y)(x+y))");
}

TEST(SparseMerge, multi_dimensional_expression_can_be_optimized)
{
    assert_optimized("merge(m1_xy,m2_xy,f(x,y)(x+y))");
    assert_optimized("merge(m1_xy,m2_xy,f(x,y)(x*y))");
}

TEST(SparseMerge, similar_expressions_are_not_optimized)
{
    assert_not_optimized("merge(scalar1,scalar2,f(x,y)(x+y))");
    assert_not_optimized("merge(dense,dense,f(x,y)(x+y))");
    assert_not_optimized("merge(mixed,mixed,f(x,y)(x+y))");
}

TEST(SparseMerge, mixed_cell_types_are_not_optimized)
{
    assert_not_optimized("merge(v1_x,v2_x_f,f(x,y)(x+y))");
    assert_not_optimized("merge(v1_x_f,v2_x,f(x,y)(x+y))");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
