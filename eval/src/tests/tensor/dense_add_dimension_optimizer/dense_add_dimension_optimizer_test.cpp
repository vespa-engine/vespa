// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_replace_type_function.h>
#include <vespa/eval/tensor/dense/dense_fast_rename_optimizer.h>
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
        .add("x5y1", spec({x(5),y(1)}, N()))
        .add("y1z1", spec({y(1),z(1)}, N()))
        .add("x5_u", spec({x(5)}, N()), "tensor(x[])")
        .add("x_m", spec({x({"a"})}, N()));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseReplaceTypeFunction>();
    EXPECT_EQUAL(info.size(), 1u);
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseReplaceTypeFunction>();
    EXPECT_TRUE(info.empty());
}

TEST("require that dimension addition can be optimized") {
    TEST_DO(verify_optimized("join(x5,tensor(y[1])(1),f(a,b)(a*b))"));
    TEST_DO(verify_optimized("join(tensor(y[1])(1),x5,f(a,b)(a*b))"));
    TEST_DO(verify_optimized("x5*tensor(y[1])(1)"));
    TEST_DO(verify_optimized("tensor(y[1])(1)*x5"));
    TEST_DO(verify_optimized("x5y1*tensor(z[1])(1)"));
    TEST_DO(verify_optimized("tensor(z[1])(1)*x5y1"));
}

TEST("require that multi-dimension addition can be optimized") {
    TEST_DO(verify_optimized("x5*tensor(a[1],b[1],c[1])(1)"));
}

TEST("require that dimension addition can be chained (and compacted)") {
    TEST_DO(verify_optimized("tensor(z[1])(1)*x5*tensor(y[1])(1)"));
}

TEST("require that constant dimension addition is optimized") {
    TEST_DO(verify_optimized("tensor(x[1])(1)*tensor(y[1])(1)"));
    TEST_DO(verify_optimized("tensor(x[1])(1.1)*tensor(y[1])(1)"));
    TEST_DO(verify_optimized("tensor(x[1])(1)*tensor(y[1])(1.1)"));
    TEST_DO(verify_optimized("tensor(x[2])(1)*tensor(y[1])(1)"));
    TEST_DO(verify_optimized("tensor(x[1])(1)*tensor(y[2])(1)"));
}

TEST("require that non-canonical dimension addition is not optimized") {
    TEST_DO(verify_not_optimized("x5+tensor(y[1])(0)"));
    TEST_DO(verify_not_optimized("tensor(y[1])(0)+x5"));
    TEST_DO(verify_not_optimized("x5-tensor(y[1])(0)"));
    TEST_DO(verify_not_optimized("x5/tensor(y[1])(1)"));
    TEST_DO(verify_not_optimized("tensor(y[1])(1)/x5"));
}

TEST("require that dimension addition with overlapping dimensions is not optimized") {
    TEST_DO(verify_not_optimized("x5*tensor(x[1],y[1])(1)"));
    TEST_DO(verify_not_optimized("tensor(x[1],y[1])(1)*x5"));
    TEST_DO(verify_not_optimized("x5y1*tensor(y[1],z[1])(1)"));
    TEST_DO(verify_not_optimized("tensor(y[1],z[1])(1)*x5y1"));
}

TEST("require that dimension addition with inappropriate dimensions is not optimized") {
    TEST_DO(verify_not_optimized("x5_u*tensor(y[1])(1)"));
    TEST_DO(verify_not_optimized("tensor(y[1])(1)*x5_u"));
    TEST_DO(verify_not_optimized("x_m*tensor(y[1])(1)"));
    TEST_DO(verify_not_optimized("tensor(y[1])(1)*x_m"));
}

TEST("require that dimension addition optimization requires unit constant tensor") {
    TEST_DO(verify_not_optimized("x5*tensor(y[1])(0.9)"));
    TEST_DO(verify_not_optimized("tensor(y[1])(1.1)*x5"));
    TEST_DO(verify_not_optimized("x5*tensor(y[1],z[2])(1)"));
    TEST_DO(verify_not_optimized("tensor(y[1],z[2])(1)*x5"));
    TEST_DO(verify_not_optimized("x5*y1z1"));
    TEST_DO(verify_not_optimized("y1z1*x5"));
    TEST_DO(verify_not_optimized("tensor(x[1])(1.1)*tensor(y[1])(1.1)"));
    TEST_DO(verify_not_optimized("tensor(x[2])(1)*tensor(y[2])(1)"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
