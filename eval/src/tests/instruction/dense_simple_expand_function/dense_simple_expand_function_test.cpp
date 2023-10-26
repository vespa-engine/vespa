// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

struct FunInfo {
    using LookFor = DenseSimpleExpandFunction;
    Inner inner;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQ(fun.inner(), inner);
    }
};

void verify_optimized(const vespalib::string &expr, Inner inner) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace all_types(CellTypeUtils::list_types(), 2);
    EvalFixture::verify<FunInfo>(expr, {FunInfo{inner}}, all_types);
}

void verify_not_optimized(const vespalib::string &expr) {
    SCOPED_TRACE(expr.c_str());
    CellTypeSpace just_double({CellType::DOUBLE}, 2);
    EvalFixture::verify<FunInfo>(expr, {}, just_double);
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

#if 0
// XXX no way to really verify this now
TEST(ExpandTest, simple_expand_is_never_inplace) {
    verify_optimized("join(@a5,@b3,f(x,y)(x*y))", Inner::RHS);
    verify_optimized("join(@b3,@a5,f(x,y)(x*y))", Inner::LHS);
}
#endif

TEST(ExpandTest, interleaved_dimensions_are_not_optimized) {
    verify_not_optimized("join(a5c3,b3,f(x,y)(x*y))");
    verify_not_optimized("join(b3,a5c3,f(x,y)(x*y))");
}

TEST(ExpandTest, matching_dimensions_are_not_expanding) {
    verify_not_optimized("join(a5c3,a5,f(x,y)(x*y))");
    verify_not_optimized("join(a5,a5c3,f(x,y)(x*y))");
}

TEST(ExpandTest, scalar_is_not_expanding) {
    verify_not_optimized("join(a5,@$1,f(x,y)(x*y))");
}

TEST(ExpandTest, unit_tensor_is_not_expanding) {
    verify_not_optimized("join(a5,x1y1z1,f(x,y)(x+y))");
    verify_not_optimized("join(x1y1z1,a5,f(x,y)(x+y))");
    verify_not_optimized("join(a1b1c1,x1y1z1,f(x,y)(x+y))");
}

TEST(ExpandTest, sparse_expand_is_not_optimized) {
    verify_not_optimized("join(a5,x1_1,f(x,y)(x*y))");
    verify_not_optimized("join(x1_1,a5,f(x,y)(x*y))");
}

TEST(ExpandTest, mixed_expand_is_not_optimized) {
    verify_not_optimized("join(a5,y1_1z2,f(x,y)(x*y))");
    verify_not_optimized("join(y1_1z2,a5,f(x,y)(x*y))");
}

GTEST_MAIN_RUN_ALL_TESTS()
