// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/instruction/sparse_no_overlap_join_function.h>
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
        .add_variants("v2_b", GenSpec(7.0).map("b", 4, 2))
        .add_variants("v2_b_trivial", GenSpec(7.0).map("b", 4, 2).idx("c", 1).idx("d", 1))
        .add("m1_ac",  GenSpec(3.0).map("a", 8, 1).map("c", 8, 1))
        .add("m2_bd",  GenSpec(17.0).map("b", 4, 2).map("d", 4, 2))
        .add("scalar", GenSpec(1.0))
        .add("dense_b",  GenSpec().idx("b", 5))
        .add("mixed_bc", GenSpec().map("b", 5, 1).idx("c", 5));
}
EvalFixture::ParamRepo param_repo = make_params();

void assert_optimized(const vespalib::string &expr) {
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EvalFixture test_fixture(test_factory, expr, param_repo, true);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(test_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(slow_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<SparseNoOverlapJoinFunction>().size(), 1u);
    EXPECT_EQ(test_fixture.find_all<SparseNoOverlapJoinFunction>().size(), 1u);
    EXPECT_EQ(slow_fixture.find_all<SparseNoOverlapJoinFunction>().size(), 0u);
}

void assert_not_optimized(const vespalib::string &expr) {
    EvalFixture fast_fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fast_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fast_fixture.find_all<SparseNoOverlapJoinFunction>().size(), 0u);
}

//-----------------------------------------------------------------------------

TEST(SparseNoOverlapJoin, expression_can_be_optimized)
{
    assert_optimized("v1_a*v2_b");
    assert_optimized("v2_b*v1_a");
    assert_optimized("m1_ac*m2_bd");
    assert_optimized("m2_bd*m1_ac");
    assert_optimized("m1_ac*v2_b");
    assert_optimized("m2_bd*v1_a");
    assert_optimized("join(v1_a,v2_b,f(x,y)(max(x,y)))");
}

TEST(SparseNoOverlapJoin, trivial_dimensions_are_ignored)
{
    assert_optimized("v1_a*v2_b_trivial");
    assert_optimized("v2_b_trivial*v1_a");
}

TEST(SparseNoOverlapJoin, overlapping_dimensions_are_not_optimized)
{
    assert_not_optimized("v1_a*v1_a");
    assert_not_optimized("v1_a*m1_ac");
    assert_not_optimized("m1_ac*v1_a");
}

TEST(SparseNoOverlapJoin, both_values_must_be_sparse_tensors)
{
    assert_not_optimized("v1_a*scalar");
    assert_not_optimized("scalar*v1_a");
    assert_not_optimized("v1_a*dense_b");
    assert_not_optimized("dense_b*v1_a");
    assert_not_optimized("v1_a*mixed_bc");
    assert_not_optimized("mixed_bc*v1_a");
}

TEST(SparseNoOverlapJoin, mixed_cell_types_are_not_optimized)
{
    assert_not_optimized("v1_a*v2_b_f");
    assert_not_optimized("v1_a_f*v2_b");
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
