// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_inplace_join_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
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

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("x5", spec({x(5)}, N()))
        .add_mutable("_d_A", spec(17.0))
        .add_mutable("_d_B", spec(42.0))
        .add_mutable("_x5", spec({x(5)}, Seq({9, 8, 7, 6, 5})))
        .add_mutable("_x5_A", spec({x(5)}, Seq({10, 11, 12, 13, 14})))
        .add_mutable("_x5_B", spec({x(5)}, Seq({16, 18, 20, 22, 24})))
        .add_mutable("_x5_C", spec({x(5)}, Seq({30, 35, 40, 45, 50})))
        .add_mutable("_x5y3", spec({x(5),y(3)}, N()))
        .add_mutable("_x5_u", spec({x(5)}, N()), "tensor(x[])")
        .add_mutable("_x_m", spec({x({"a", "b", "c"})}, N()));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_left_optimized(const vespalib::string &expr, size_t cnt) {
    EvalFixture fixture(prod_engine, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.get_param(0), fixture.result());
    auto info = fixture.find_all<DenseInplaceJoinFunction>();
    ASSERT_EQUAL(info.size(), cnt);
    for (size_t i = 0; i < cnt; ++i) {
        EXPECT_TRUE(info[i]->result_is_mutable());
    }
}

void verify_right_optimized(const vespalib::string &expr, size_t cnt) {
    EvalFixture fixture(prod_engine, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.get_param(1), fixture.result());
    auto info = fixture.find_all<DenseInplaceJoinFunction>();
    ASSERT_EQUAL(info.size(), cnt);
    for (size_t i = 0; i < cnt; ++i) {
        EXPECT_TRUE(info[i]->result_is_mutable());
    }
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_engine, expr, param_repo, true, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_NOT_EQUAL(fixture.get_param(0), fixture.result());
    auto info = fixture.find_all<DenseInplaceJoinFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that mutable dense concrete tensors are optimized") {
    TEST_DO(verify_left_optimized("_x5_A+_x5_B", 1));
    TEST_DO(verify_left_optimized("_x5y3+_x5y3", 1));
    TEST_DO(verify_left_optimized("_x5_A-_x5_B", 1));
    TEST_DO(verify_left_optimized("_x5-x5", 1));
    TEST_DO(verify_right_optimized("x5-_x5", 1));
}

TEST("require that inplace join operations can be chained") {
    TEST_DO(verify_left_optimized("_x5_C-(_x5_B-_x5_A)", 2));
}

TEST("require that abstract tensors are not optimized") {
    TEST_DO(verify_not_optimized("_x5_u+_x5"));
    TEST_DO(verify_not_optimized("_x5_u+_x5_u"));
}

TEST("require that non-mutable tensors are not optimized") {
    TEST_DO(verify_not_optimized("x5+x5"));
}

TEST("require that scalar values are not optimized") {
    TEST_DO(verify_not_optimized("_d_A+_d_B"));
    TEST_DO(verify_not_optimized("join(_d_A,_d_B,f(x,y)(x+y))"));
}

TEST("require that mapped tensors are not optimized") {
    TEST_DO(verify_not_optimized("_x_m+_x_m"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
