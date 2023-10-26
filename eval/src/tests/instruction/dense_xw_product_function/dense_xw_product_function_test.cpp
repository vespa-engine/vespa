// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/instruction/dense_xw_product_function.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

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
        EXPECT_EQUAL(fun.vector_size(), vec_size);
        EXPECT_EQUAL(fun.result_size(), res_size);
        EXPECT_EQUAL(fun.common_inner(), happy);
    }
};

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture::verify<FunInfo>(expr, {}, CellTypeSpace({CellType::FLOAT}, 2));
}

void verify_optimized(const vespalib::string &expr, size_t vec_size, size_t res_size, bool happy) {
    EvalFixture::verify<FunInfo>(expr, {{vec_size, res_size, happy}}, CellTypeSpace(CellTypeUtils::list_types(), 2));
}

vespalib::string make_expr(const vespalib::string &a, const vespalib::string &b, const vespalib::string &common) {
    return make_string("reduce(%s*%s,sum,%s)", a.c_str(), b.c_str(), common.c_str());
}

void verify_optimized_multi(const vespalib::string &a, const vespalib::string &b, const vespalib::string &common,
                            size_t vec_size, size_t res_size, bool happy)
{
    {
        auto expr = make_expr(a, b, common);
        TEST_STATE(expr.c_str());
        TEST_DO(verify_optimized(expr, vec_size, res_size, happy));
    }
    {
        auto expr = make_expr(b, a, common);
        TEST_STATE(expr.c_str());
        TEST_DO(verify_optimized(expr, vec_size, res_size, happy));
    }
}

TEST("require that xw product gives same results as reference join/reduce") {
    // 1 -> 1 happy/unhappy
    TEST_DO(verify_optimized_multi("y1", "x1y1", "y", 1, 1, true));
    TEST_DO(verify_optimized_multi("y1", "y1z1", "y", 1, 1, false));
    // 3 -> 2 happy/unhappy
    TEST_DO(verify_optimized_multi("y3", "x2y3", "y", 3, 2, true));
    TEST_DO(verify_optimized_multi("y3", "y3z2", "y", 3, 2, false));
    // 5 -> 8 happy/unhappy
    TEST_DO(verify_optimized_multi("y5", "x8y5", "y", 5, 8, true));
    TEST_DO(verify_optimized_multi("y5", "y5z8", "y", 5, 8, false));
    // 16 -> 5 happy/unhappy
    TEST_DO(verify_optimized_multi("y16", "x5y16", "y", 16, 5, true));
    TEST_DO(verify_optimized_multi("y16", "y16z5", "y", 16, 5, false));
}

TEST("require that various variants of xw product can be optimized") {
    TEST_DO(verify_optimized("reduce(join(y3,x2y3,f(x,y)(x*y)),sum,y)", 3, 2, true));
}

TEST("require that expressions similar to xw product are not optimized") {
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,sum,x)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,prod,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,sum)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*x)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(x+y)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(x*x)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*y)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*x*1)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2z3,sum,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2z3,sum,z)"));
}

TEST("require that xw product can be debug dumped") {
    EvalFixture::ParamRepo param_repo;
    param_repo.add("y5", GenSpec::from_desc("y5"));
    param_repo.add("x8y5", GenSpec::from_desc("x8y5"));
    EvalFixture fixture(EvalFixture::prod_factory(), "reduce(y5*x8y5,sum,y)", param_repo, true);
    auto info = fixture.find_all<DenseXWProductFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    fprintf(stderr, "%s\n", info[0]->as_string().c_str());
}

TEST_MAIN() { TEST_RUN_ALL(); }
