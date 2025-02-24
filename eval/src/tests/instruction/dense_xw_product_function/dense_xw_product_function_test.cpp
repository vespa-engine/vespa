// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/dense_xw_product_function.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/stringfmt.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

struct FunInfo {
    using LookFor = DenseXWProductFunction;
    size_t vec_size;
    size_t res_size;
    bool happy;
    void verify(const LookFor &fun) const {
        EXPECT_TRUE(fun.result_is_mutable());
        EXPECT_EQ(fun.vector_size(), vec_size);
        EXPECT_EQ(fun.result_size(), res_size);
        EXPECT_EQ(fun.common_inner(), happy);
    }
};

void verify_not_optimized(const std::string &expr) {
    SCOPED_TRACE(expr);
    EvalFixture::verify<FunInfo>(expr, {}, CellTypeSpace({CellType::FLOAT}, 2));
}

void verify_optimized(const std::string &expr, size_t vec_size, size_t res_size, bool happy) {
    SCOPED_TRACE(expr);
    EvalFixture::verify<FunInfo>(expr, {{vec_size, res_size, happy}}, CellTypeSpace(CellTypeUtils::list_types(), 2));
}

std::string make_expr(const std::string &a, const std::string &b, const std::string &common) {
    return make_string("reduce(%s*%s,sum,%s)", a.c_str(), b.c_str(), common.c_str());
}

void verify_optimized_multi(const std::string &a, const std::string &b, const std::string &common,
                            size_t vec_size, size_t res_size, bool happy)
{
    SCOPED_TRACE(make_string("verify_optimized_multi(\"%s\",\"%s\",...)", a.c_str(), b.c_str()));
    {
        auto expr = make_expr(a, b, common);
        SCOPED_TRACE(expr);
        verify_optimized(expr, vec_size, res_size, happy);
    }
    {
        auto expr = make_expr(b, a, common);
        SCOPED_TRACE(expr);
        verify_optimized(expr, vec_size, res_size, happy);
    }
}

TEST(DenseXWProductFunctionTest, require_that_xw_product_gives_same_results_as_reference_join_reduce)
{
    // 1 -> 1 happy/unhappy
    verify_optimized_multi("y1", "x1y1", "y", 1, 1, true);
    verify_optimized_multi("y1", "y1z1", "y", 1, 1, false);
    // 3 -> 2 happy/unhappy
    verify_optimized_multi("y3", "x2y3", "y", 3, 2, true);
    verify_optimized_multi("y3", "y3z2", "y", 3, 2, false);
    // 5 -> 8 happy/unhappy
    verify_optimized_multi("y5", "x8y5", "y", 5, 8, true);
    verify_optimized_multi("y5", "y5z8", "y", 5, 8, false);
    // 16 -> 5 happy/unhappy
    verify_optimized_multi("y16", "x5y16", "y", 16, 5, true);
    verify_optimized_multi("y16", "y16z5", "y", 16, 5, false);
}

TEST(DenseXWProductFunctionTest, require_that_various_variants_of_xw_product_can_be_optimized)
{
    verify_optimized("reduce(join(y3,x2y3,f(x,y)(x*y)),sum,y)", 3, 2, true);
}

TEST(DenseXWProductFunctionTest, require_that_expressions_similar_to_xw_product_are_not_optimized)
{
    verify_not_optimized("reduce(y3*x2y3,sum,x)");
    verify_not_optimized("reduce(y3*x2y3,prod,y)");
    verify_not_optimized("reduce(y3*x2y3,sum)");
    verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*x)),sum,y)");
    verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(x+y)),sum,y)");
    verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(x*x)),sum,y)");
    verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*y)),sum,y)");
    verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*x*1)),sum,y)");
    verify_not_optimized("reduce(y3*x2z3,sum,y)");
    verify_not_optimized("reduce(y3*x2z3,sum,z)");
}

TEST(DenseXWProductFunctionTest, require_that_xw_product_can_be_debug_dumped)
{
    EvalFixture::ParamRepo param_repo;
    param_repo.add("y5", GenSpec::from_desc("y5"));
    param_repo.add("x8y5", GenSpec::from_desc("x8y5"));
    EvalFixture fixture(EvalFixture::prod_factory(), "reduce(y5*x8y5,sum,y)", param_repo, true);
    auto info = fixture.find_all<DenseXWProductFunction>();
    ASSERT_EQ(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    fprintf(stderr, "%s\n", info[0]->as_string().c_str());
}

GTEST_MAIN_RUN_ALL_TESTS()
