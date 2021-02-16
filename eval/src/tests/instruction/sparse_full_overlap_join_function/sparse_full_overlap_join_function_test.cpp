// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/instruction/sparse_full_overlap_join_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::eval::test;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();
const ValueBuilderFactory &test_factory = SimpleValueBuilderFactory::get();

//-----------------------------------------------------------------------------

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add_variants("v1_a", GenSpec(3.0).map("a", 8, 1))
        .add_variants("v2_a", GenSpec(7.0).map("a", 4, 2))
        .add_variants("v2_a_trivial", GenSpec(7.0).map("a", 4, 2).idx("b", 1).idx("c", 1))
        .add_variants("v3_b", GenSpec(5.0).map("b", 4, 2))
        .add("m1_ab",  GenSpec(3.0).map("a", 8, 1).map("b", 8, 1))
        .add("m2_ab",  GenSpec(17.0).map("a", 4, 2).map("b", 4, 2))
        .add("m3_bc",  GenSpec(11.0).map("b", 4, 2).map("c", 4, 2))
        .add("scalar", GenSpec(1.0))
        .add("dense_a",  GenSpec().idx("a", 5))
        .add("mixed_ab", GenSpec().map("a", 5, 1).idx("b", 5));
}
EvalFixture::ParamRepo param_repo = make_params();

void assert_optimized(const vespalib::string &expr) {
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EvalFixture test_fixture(test_factory, expr, param_repo, true);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(test_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<SparseFullOverlapJoinFunction>().size(), 1u);
    EXPECT_EQ(test_fixture.find_all<SparseFullOverlapJoinFunction>().size(), 1u);
    EXPECT_EQ(slow_fixture.find_all<SparseFullOverlapJoinFunction>().size(), 0u);
}

void assert_not_optimized(const vespalib::string &expr) {
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<SparseFullOverlapJoinFunction>().size(), 0u);
}

//-----------------------------------------------------------------------------

TEST(SparseFullOverlapJoin, expression_can_be_optimized)
{
    assert_optimized("v1_a-v2_a");
    assert_optimized("v2_a-v1_a");
    assert_optimized("join(v1_a,v2_a,f(x,y)(max(x,y)))");
}

TEST(SparseFullOverlapJoin, multi_dimensional_expression_can_be_optimized)
{
    assert_optimized("m1_ab-m2_ab");
    assert_optimized("m2_ab-m1_ab");
    assert_optimized("join(m1_ab,m2_ab,f(x,y)(max(x,y)))");
}

TEST(SparseFullOverlapJoin, trivial_dimensions_are_ignored)
{
    assert_optimized("v1_a*v2_a_trivial");
    assert_optimized("v2_a_trivial*v1_a");
}

TEST(SparseFullOverlapJoin, inappropriate_shapes_are_not_optimized)
{
    assert_not_optimized("v1_a*scalar");
    assert_not_optimized("v1_a*mixed_ab");
    assert_not_optimized("v1_a*v3_b");
    assert_not_optimized("v1_a*m1_ab");
    assert_not_optimized("m1_ab*m3_bc");
    assert_not_optimized("scalar*scalar");
    assert_not_optimized("dense_a*dense_a");
    assert_not_optimized("mixed_ab*mixed_ab");
}

TEST(SparseFullOverlapJoin, mixed_cell_types_are_not_optimized)
{
    assert_not_optimized("v1_a*v2_a_f");
    assert_not_optimized("v1_a_f*v2_a");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
