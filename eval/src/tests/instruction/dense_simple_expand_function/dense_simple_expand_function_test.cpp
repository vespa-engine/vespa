// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/instruction/dense_simple_expand_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

using Inner = DenseSimpleExpandFunction::Inner;

const TensorEngine &old_engine = tensor::DefaultTensorEngine::ref();
const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("a", spec(1.5))
        .add("sparse", spec({x({"a"})}, N()))
        .add("mixed", spec({y({"a"}),z(5)}, N()))
        .add_vector("a", 5)
        .add_vector("b", 3)
        .add_cube("A", 1, "a", 5, "c", 1)
        .add_cube("B", 1, "b", 3, "c", 1)
        .add_matrix("a", 5, "c", 3)
        .add_matrix("x", 3, "y", 2)
        .add_cube("a", 1, "b", 1, "c", 1)
        .add_cube("x", 1, "y", 1, "z", 1);
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

    EvalFixture old_slow_fixture(old_engine, expr, param_repo, false);
    EvalFixture old_fixture(old_engine, expr, param_repo, true, true);
    EXPECT_EQ(old_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(old_fixture.result(), old_slow_fixture.result());
    info = old_fixture.find_all<DenseSimpleExpandFunction>();
    ASSERT_EQ(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQ(info[0]->inner(), inner);
    ASSERT_EQ(old_fixture.num_params(), 2);
    EXPECT_TRUE(!(old_fixture.get_param(0) == old_fixture.result()));
    EXPECT_TRUE(!(old_fixture.get_param(1) == old_fixture.result()));
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseSimpleExpandFunction>();
    EXPECT_TRUE(info.empty());

    EvalFixture old_slow_fixture(old_engine, expr, param_repo, false);
    EvalFixture old_fixture(old_engine, expr, param_repo, true);
    EXPECT_EQ(old_fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(old_fixture.result(), old_slow_fixture.result());
    info = old_fixture.find_all<DenseSimpleExpandFunction>();
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
    verify_optimized("join(a5,b3f,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(a5f,b3,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(a5f,b3f,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(b3,a5f,f(x,y)(x*y))", Inner::LHS);
    verify_optimized("join(b3f,a5,f(x,y)(x*y))", Inner::LHS);
    verify_optimized("join(b3f,a5f,f(x,y)(x*y))", Inner::LHS);
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
