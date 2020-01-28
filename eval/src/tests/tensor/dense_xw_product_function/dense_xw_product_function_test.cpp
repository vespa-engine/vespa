// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_xw_product_function.h>
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

struct First {
    bool value;
    explicit First(bool value_in) : value(value_in) {}
    operator bool() const { return value; }
};

struct MyVecSeq : Sequence {
    double operator[](size_t i) const override { return (3.0 + i) * 7.0; }
};

struct MyMatSeq : Sequence {
    double operator[](size_t i) const override { return (5.0 + i) * 43.0; }
};

void add_vector(EvalFixture::ParamRepo &repo, const char *d1, size_t s1) {
    auto name = make_string("%s%zu", d1, s1);
    auto layout = Layout({{d1, s1}});
    repo.add(name, spec(layout, MyVecSeq()));
    repo.add(name + "f", spec(float_cells(layout), MyVecSeq()));
}

void add_matrix(EvalFixture::ParamRepo &repo, const char *d1, size_t s1, const char *d2, size_t s2) {
    auto name = make_string("%s%zu%s%zu", d1, s1, d2, s2);
    auto layout = Layout({{d1, s1}, {d2, s2}});
    repo.add(name, spec(layout, MyMatSeq()));
    repo.add(name + "f", spec(float_cells(layout), MyMatSeq()));
}

EvalFixture::ParamRepo make_params() {
    EvalFixture::ParamRepo repo;
    add_vector(repo, "y",  1);
    add_vector(repo, "y",  3);
    add_vector(repo, "y",  5);
    add_vector(repo, "y", 16);
    add_matrix(repo, "x",  1, "y",  1);
    add_matrix(repo, "y",  1, "z",  1);
    add_matrix(repo, "x",  2, "y",  3);
    add_matrix(repo, "y",  3, "z",  2);
    add_matrix(repo, "x",  2, "z",  3);
    add_matrix(repo, "x",  8, "y",  5);
    add_matrix(repo, "y",  5, "z",  8);
    add_matrix(repo, "x",  5, "y", 16);
    add_matrix(repo, "y", 16, "z",  5);
    return repo;
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, size_t vec_size, size_t res_size, bool happy) {
    EvalFixture slow_fixture(prod_engine, expr, param_repo, false);
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseXWProductFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQUAL(info[0]->vectorSize(), vec_size);
    EXPECT_EQUAL(info[0]->resultSize(), res_size);
    EXPECT_EQUAL(info[0]->matrixHasCommonDimensionInnermost(), happy);
}

vespalib::string make_expr(const vespalib::string &a, const vespalib::string &b, const vespalib::string &common,
                           bool float_a, bool float_b)
{
    return make_string("reduce(%s%s*%s%s,sum,%s)", a.c_str(), float_a ? "f" : "", b.c_str(), float_b ? "f" : "", common.c_str());
}

void verify_optimized_multi(const vespalib::string &a, const vespalib::string &b, const vespalib::string &common,
                            size_t vec_size, size_t res_size, bool happy, First first = First(true))
{
    for (bool float_a: {false, true}) {
        for (bool float_b: {false, true}) {
            auto expr = make_expr(a, b, common, float_a, float_b);
            TEST_STATE(expr.c_str());
            TEST_DO(verify_optimized(expr, vec_size, res_size, happy));
        }
    }
    if (first) {
        TEST_DO(verify_optimized_multi(b, a, common, vec_size, res_size, happy, First(false)));
    }
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_engine, expr, param_repo, false);
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseXWProductFunction>();
    EXPECT_TRUE(info.empty());
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
    TEST_DO(verify_optimized("reduce(join(y3,x2y3,f(x,y)(y*x)),sum,y)", 3, 2, true));
}

TEST("require that expressions similar to xw product are not optimized") {
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,sum,x)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,prod,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2y3,sum)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(x+y)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(x*x)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*y)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(join(y3,x2y3,f(x,y)(y*x*1)),sum,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2z3,sum,y)"));
    TEST_DO(verify_not_optimized("reduce(y3*x2z3,sum,z)"));
}

TEST("require that xw product can be debug dumped") {
    EvalFixture fixture(prod_engine, "reduce(y5*x8y5,sum,y)", param_repo, true);
    auto info = fixture.find_all<DenseXWProductFunction>();
    ASSERT_EQUAL(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    fprintf(stderr, "%s\n", info[0]->as_string().c_str());
}

TEST_MAIN() { TEST_RUN_ALL(); }
