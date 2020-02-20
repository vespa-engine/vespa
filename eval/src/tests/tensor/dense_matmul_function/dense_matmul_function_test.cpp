// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_matmul_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/eval/eval/test/eval_fixture.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::tensor;
using namespace vespalib::eval::tensor_function;

const TensorEngine &prod_engine = DefaultTensorEngine::ref();

void add_matrix(EvalFixture::ParamRepo &repo, const char *d1, size_t s1, const char *d2, size_t s2) {
    for (bool float_cells: {false, true}) {
        auto name = make_string("%s%zu%s%zu%s", d1, s1, d2, s2, float_cells ? "f" : "");
        auto type_str = make_string("tensor%s(%s[%zu],%s[%zu])", float_cells ? "<float>" : "", d1, s1, d2, s2);
        TensorSpec matrix(type_str);
        for (size_t i = 0; i < s1; ++i) {
            for (size_t j = 0; j < s2; ++j) {
                double value = (i + s1 + s2) * 3.0 + (j + s2) * 7.0;
                matrix.add({{d1, i}, {d2, j}}, value);
            }
        }
        repo.add(name, matrix);
    }
}

EvalFixture::ParamRepo make_params() {
    EvalFixture::ParamRepo repo;
    add_matrix(repo, "a", 2, "d", 3); // inner/inner
    add_matrix(repo, "a", 2, "b", 5); // inner/outer
    add_matrix(repo, "b", 5, "c", 2); // outer/outer
    add_matrix(repo, "a", 2, "c", 3); // not matching
    //-----------------------------------------------
    add_matrix(repo, "b", 5, "d", 3); // fixed param
    return repo;
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr,
                      size_t lhs_size, size_t common_size, size_t rhs_size,
                      bool lhs_inner, bool rhs_inner)
{
    EvalFixture slow_fixture(prod_engine, expr, param_repo, false);
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseMatMulFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQUAL(info[0]->lhs_size(), lhs_size);
    EXPECT_EQUAL(info[0]->common_size(), common_size);
    EXPECT_EQUAL(info[0]->rhs_size(), rhs_size);
    EXPECT_EQUAL(info[0]->lhs_common_inner(), lhs_inner);
    EXPECT_EQUAL(info[0]->rhs_common_inner(), rhs_inner);
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_engine, expr, param_repo, false);
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseMatMulFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that matmul can be optimized") {
    TEST_DO(verify_optimized("reduce(a2d3*b5d3,sum,d)", 2, 3, 5, true, true));
}

TEST("require that matmul with lambda can be optimized") {
    TEST_DO(verify_optimized("reduce(join(a2d3,b5d3,f(x,y)(x*y)),sum,d)", 2, 3, 5, true, true));
    TEST_DO(verify_optimized("reduce(join(a2d3,b5d3,f(x,y)(y*x)),sum,d)", 2, 3, 5, true, true));
}

TEST("require that expressions similar to matmul are not optimized") {
    TEST_DO(verify_not_optimized("reduce(a2d3*b5d3,sum,a)"));
    TEST_DO(verify_not_optimized("reduce(a2d3*b5d3,sum,b)"));
    TEST_DO(verify_not_optimized("reduce(a2d3*b5d3,prod,d)"));
    TEST_DO(verify_not_optimized("reduce(a2d3*b5d3,sum)"));
    TEST_DO(verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(x+y)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(x*x)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(y*y)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(join(a2d3,b5d3,f(x,y)(x*y*1)),sum,d)"));
    TEST_DO(verify_not_optimized("reduce(a2c3*b5d3,sum,d)"));
    TEST_DO(verify_not_optimized("reduce(a2c3*b5d3,sum,c)"));
}

TEST("require that xw product can be debug dumped") {
    EvalFixture fixture(prod_engine, "reduce(a2d3*b5d3,sum,d)", param_repo, true);
    auto info = fixture.find_all<DenseMatMulFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    fprintf(stderr, "%s\n", info[0]->as_string().c_str());
}

vespalib::string make_expr(const vespalib::string &a, const vespalib::string &b, const vespalib::string &common,
                           bool float_a, bool float_b)
{
    return make_string("reduce(%s%s*%s%s,sum,%s)", a.c_str(), float_a ? "f" : "", b.c_str(), float_b ? "f" : "", common.c_str());
}

void verify_optimized_multi(const vespalib::string &a, const vespalib::string &b, const vespalib::string &common,
                            size_t lhs_size, size_t common_size, size_t rhs_size,
                            bool lhs_inner, bool rhs_inner)
{
    for (bool float_a: {false, true}) {
        for (bool float_b: {false, true}) {
            {
                auto expr = make_expr(a, b, common, float_a, float_b);
                TEST_STATE(expr.c_str());
                TEST_DO(verify_optimized(expr, lhs_size, common_size, rhs_size, lhs_inner, rhs_inner));
            }
            {
                auto expr = make_expr(b, a, common, float_b, float_a);
                TEST_STATE(expr.c_str());
                TEST_DO(verify_optimized(expr, lhs_size, common_size, rhs_size, lhs_inner, rhs_inner));
            }
        }
    }
}

TEST("require that matmul inner/inner works correctly") {
    TEST_DO(verify_optimized_multi("a2d3", "b5d3", "d", 2, 3, 5, true, true));
}

TEST("require that matmul inner/outer works correctly") {
    TEST_DO(verify_optimized_multi("a2b5", "b5d3", "b", 2, 5, 3, true, false));
}

TEST("require that matmul outer/outer works correctly") {
    TEST_DO(verify_optimized_multi("b5c2", "b5d3", "b", 2, 5, 3, false, false));
}

TEST_MAIN() { TEST_RUN_ALL(); }
