// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/log/log.h>
LOG_SETUP("dense_dot_product_function_test");

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/simple_tensor.h>
#include <vespa/eval/eval/simple_tensor_engine.h>
#include <vespa/eval/tensor/default_tensor_engine.h>
#include <vespa/eval/tensor/dense/dense_xw_product_function.h>
#include <vespa/eval/tensor/dense/dense_tensor.h>
#include <vespa/eval/tensor/dense/dense_tensor_builder.h>
#include <vespa/eval/tensor/dense/dense_tensor_view.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::tensor;
using namespace vespalib::eval::tensor_function;

const TensorEngine &ref_engine = SimpleTensorEngine::ref();
const TensorEngine &prod_engine = DefaultTensorEngine::ref();

void verify_equal(const Value &expect, const Value &value) {
    const eval::Tensor *tensor = value.as_tensor();
    ASSERT_TRUE(tensor != nullptr);
    const eval::Tensor *expect_tensor = expect.as_tensor();
    ASSERT_TRUE(expect_tensor != nullptr);
    auto expect_spec = expect_tensor->engine().to_spec(expect);
    auto value_spec = tensor->engine().to_spec(value);
    EXPECT_EQUAL(expect_spec, value_spec);
}

SimpleObjectParams wrap(std::vector<eval::Value::CREF> params) {
    return SimpleObjectParams(params);
}

void verify_result(const TensorSpec &v, const TensorSpec &m, bool happy) {
    Stash stash;
    Value::UP ref_vec = ref_engine.from_spec(v);
    Value::UP ref_mat = ref_engine.from_spec(m);
    const Value &joined = ref_engine.join(*ref_vec, *ref_mat, operation::Mul::f, stash);
    const Value &expect = ref_engine.reduce(joined, Aggr::SUM, {"x"}, stash);

    Value::UP prod_vec = prod_engine.from_spec(v);
    Value::UP prod_mat = prod_engine.from_spec(m);

    Inject vec_first(prod_vec->type(), 0);
    Inject mat_last(prod_mat->type(), 1);

    DenseXWProductFunction fun1(expect.type(), vec_first, mat_last,
                                prod_vec->type().dimensions()[0].size,
                                expect.type().dimensions()[0].size,
                                happy);
    const Value &actual1 = fun1.eval(prod_engine, wrap({*prod_vec, *prod_mat}), stash);
    TEST_DO(verify_equal(expect, actual1));

    Inject vec_last(prod_vec->type(), 1);
    Inject mat_first(prod_mat->type(), 0);

    DenseXWProductFunction fun2(expect.type(), vec_last, mat_first,
                                prod_vec->type().dimensions()[0].size,
                                expect.type().dimensions()[0].size,
                                happy);
    const Value &actual2 = fun2.eval(prod_engine, wrap({*prod_mat, *prod_vec}), stash);
    TEST_DO(verify_equal(expect, actual2));
}

TensorSpec make_vector(const vespalib::string &name, size_t sz) {
    TensorSpec ret(make_string("tensor(%s[%zu])", name.c_str(), sz));
    for (size_t i = 0; i < sz; ++i) {
        ret.add({{name, i}}, (1.0 + i) * 16.0);
    }
    return ret;
}

TensorSpec make_matrix(const vespalib::string &d1name, size_t d1sz,
                       const vespalib::string &d2name, size_t d2sz)
{
    TensorSpec ret(make_string("tensor(%s[%zu],%s[%zu])",
                               d1name.c_str(), d1sz,
                               d2name.c_str(), d2sz));
    for (size_t i = 0; i < d1sz; ++i) {
        for (size_t j = 0; j < d2sz; ++j) {
            ret.add({{d1name,i},{d2name,j}}, 1.0 + i*7.0 + j*43.0);
        }
    }
    return ret;
}

TEST("require that xw product gives same results as reference join/reduce") {
    verify_result(make_vector("x", 1), make_matrix("o", 1, "x", 1), true);
    verify_result(make_vector("x", 1), make_matrix("x", 1, "y", 1), false);

    verify_result(make_vector("x", 3), make_matrix("o", 2, "x", 3), true);
    verify_result(make_vector("x", 3), make_matrix("x", 3, "y", 2), false);

    verify_result(make_vector("x", 5), make_matrix("o", 8, "x", 5), true);
    verify_result(make_vector("x", 5), make_matrix("x", 5, "y", 8), false);

    verify_result(make_vector("x", 16), make_matrix("o", 5, "x", 16), true);
    verify_result(make_vector("x", 16), make_matrix("x", 16, "y", 5), false);
}

TEST_MAIN() { TEST_RUN_ALL(); }
