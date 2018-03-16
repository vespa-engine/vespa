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
        .add("x5_u", spec({x(5)}, N()), "tensor(x[])")
        .add("x_m", spec({x({"a", "b", "c"})}, N()))
        .add("x5y3", spec({x(5),y(3)}, N()));
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

TEST("require that non-transposing dense renames are optimized") {
    TEST_DO(verify_optimized("rename(x5,x,y)"));
    TEST_DO(verify_optimized("rename(x5,x,a)"));
    TEST_DO(verify_optimized("rename(x5y3,y,z)"));
    TEST_DO(verify_optimized("rename(x5y3,x,a)"));
    TEST_DO(verify_optimized("rename(x5y3,(x,y),(a,b))"));
    TEST_DO(verify_optimized("rename(x5y3,(x,y),(z,zz))"));
    TEST_DO(verify_optimized("rename(x5y3,(x,y),(y,z))"));
    TEST_DO(verify_optimized("rename(x5y3,(y,x),(b,a))"));
}

TEST("require that transposing dense renames are not optimized") {
    TEST_DO(verify_not_optimized("rename(x5y3,x,z)"));
    TEST_DO(verify_not_optimized("rename(x5y3,y,a)"));
    TEST_DO(verify_not_optimized("rename(x5y3,(x,y),(y,x))"));
    TEST_DO(verify_not_optimized("rename(x5y3,(x,y),(b,a))"));
    TEST_DO(verify_not_optimized("rename(x5y3,(y,x),(a,b))"));
}

TEST("require that abstract dense renames are not optimized") {
    TEST_DO(verify_not_optimized("rename(x5_u,x,y)"));
}

TEST("require that non-dense renames are not optimized") {
    TEST_DO(verify_not_optimized("rename(x_m,x,y)"));
}

TEST("require that chained optimized renames are compacted into a single operation") {
    TEST_DO(verify_optimized("rename(rename(x5,x,y),y,z)"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
