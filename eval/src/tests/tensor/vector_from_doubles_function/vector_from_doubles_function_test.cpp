// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/vector_from_doubles_function.h>
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
        .add("a", spec(1.0))
        .add("b", spec(2.0))
        .add("c", spec(3.0))
        .add("d", spec(4.0))
        .add("x5", spec({x(5)}, N()));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify(const vespalib::string &expr, size_t expect_optimized_cnt, size_t expect_not_optimized_cnt) {
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<VectorFromDoublesFunction>();
    EXPECT_EQUAL(info.size(), expect_optimized_cnt);
    for (size_t i = 0; i < info.size(); ++i) {
        EXPECT_TRUE(info[i]->result_is_mutable());
    }
    EXPECT_EQUAL(fixture.find_all<Concat>().size(), expect_not_optimized_cnt);
}

//-----------------------------------------------------------------------------

TEST("require that multiple concats are optimized") {
    TEST_DO(verify("concat(a,b,x)", 1, 0));
    TEST_DO(verify("concat(a,concat(b,concat(c,d,x),x),x)", 1, 0));
    TEST_DO(verify("concat(concat(concat(a,b,x),c,x),d,x)", 1, 0));
    TEST_DO(verify("concat(concat(a,b,x),concat(c,d,x),x)", 1, 0));
}

TEST("require that concat along different dimension is not optimized") {
    TEST_DO(verify("concat(concat(a,b,x),concat(c,d,x),y)", 2, 1));
}

TEST("require that concat of vector and double is not optimized") {
    TEST_DO(verify("concat(a,x5,x)", 0, 1));
    TEST_DO(verify("concat(x5,b,x)", 0, 1));
}

TEST_MAIN() { TEST_RUN_ALL(); }
