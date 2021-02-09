// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/dense_simple_expand_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

using Inner = DenseSimpleExpandFunction::Inner;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("a", GenSpec(1.5))
        .add("sparse", GenSpec().map("x", {"a"}))
        .add("mixed", GenSpec().map("y", {"a"}).idx("z", 5))
        .add_variants("a5", GenSpec().idx("a", 5))
        .add_variants("b3", GenSpec().idx("b", 3))
        .add_variants("A1a5c1", GenSpec().idx("A", 1).idx("a", 5).idx("c", 1))
        .add_variants("B1b3c1", GenSpec().idx("B", 1).idx("b", 3).idx("c", 1))
        .add_variants("a5c3", GenSpec().idx("a", 5).idx("c", 3))
        .add_variants("x3y2", GenSpec().idx("x", 3).idx("y", 2))
        .add_variants("a1b1c1", GenSpec().idx("a", 1).idx("b", 1).idx("c", 1))
        .add_variants("x1y1z1", GenSpec().idx("x", 1).idx("y", 1).idx("z", 1));
}

EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, Inner inner) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseSimpleExpandFunction>();
    ASSERT_EQ(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQ(info[0]->inner(), inner);
    ASSERT_EQ(fixture.num_params(), 2);
    EXPECT_TRUE(!(fixture.get_param(0) == fixture.result()));
    EXPECT_TRUE(!(fixture.get_param(1) == fixture.result()));
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseSimpleExpandFunction>();
    EXPECT_TRUE(info.empty());
}

TEST(ExpandTest, simple_expand_is_optimized) {
    verify_optimized("join(a5,b3,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(b3,a5,f(x,y)(x*y))", Inner::LHS);
}

TEST(ExpandTest, multiple_dimensions_are_supported) {
    verify_optimized("join(a5,x3y2,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(x3y2,a5,f(x,y)(x*y))", Inner::LHS);
    verify_optimized("join(a5c3,x3y2,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(x3y2,a5c3,f(x,y)(x*y))", Inner::LHS);
}

TEST(ExpandTest, trivial_dimensions_are_ignored) {
    verify_optimized("join(A1a5c1,B1b3c1,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(B1b3c1,A1a5c1,f(x,y)(x*y))", Inner::LHS);
}

TEST(ExpandTest, simple_expand_handles_asymmetric_operations_correctly) {
    verify_optimized("join(a5,b3,f(x,y)(x-y))", Inner::RHS);
    verify_optimized("join(b3,a5,f(x,y)(x-y))", Inner::LHS);
    verify_optimized("join(a5,b3,f(x,y)(x/y))", Inner::RHS);
    verify_optimized("join(b3,a5,f(x,y)(x/y))", Inner::LHS);
}

TEST(ExpandTest, simple_expand_can_have_various_cell_types) {
    verify_optimized("join(a5,b3_f,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(a5_f,b3,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(a5_f,b3_f,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(b3,a5_f,f(x,y)(x*y))", Inner::LHS);
    verify_optimized("join(b3_f,a5,f(x,y)(x*y))", Inner::LHS);
    verify_optimized("join(b3_f,a5_f,f(x,y)(x*y))", Inner::LHS);
}

TEST(ExpandTest, simple_expand_is_never_inplace) {
    verify_optimized("join(@a5,@b3,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(@b3,@a5,f(x,y)(x*y))", Inner::LHS);
}

TEST(ExpandTest, interleaved_dimensions_are_not_optimized) {
    verify_not_optimized("join(a5c3,b3,f(x,y)(x*y))");
    verify_not_optimized("join(b3,a5c3,f(x,y)(x*y))");
}

TEST(ExpandTest, matching_dimensions_are_not_expanding) {
    verify_not_optimized("join(a5c3,a5,f(x,y)(x*y))");
    verify_not_optimized("join(a5,a5c3,f(x,y)(x*y))");
}

TEST(ExpandTest, scalar_is_not_expanding) {
    verify_not_optimized("join(a5,a,f(x,y)(x*y))");
}

TEST(ExpandTest, unit_tensor_is_not_expanding) {
    verify_not_optimized("join(a5,x1y1z1,f(x,y)(x+y))");
    verify_not_optimized("join(x1y1z1,a5,f(x,y)(x+y))");
    verify_not_optimized("join(a1b1c1,x1y1z1,f(x,y)(x+y))");
}

TEST(ExpandTest, sparse_expand_is_not_optimized) {
    verify_not_optimized("join(a5,sparse,f(x,y)(x*y))");
    verify_not_optimized("join(sparse,a5,f(x,y)(x*y))");
}

TEST(ExpandTest, mixed_expand_is_not_optimized) {
    verify_not_optimized("join(a5,mixed,f(x,y)(x*y))");
    verify_not_optimized("join(mixed,a5,f(x,y)(x*y))");
}

GTEST_MAIN_RUN_ALL_TESTS()
