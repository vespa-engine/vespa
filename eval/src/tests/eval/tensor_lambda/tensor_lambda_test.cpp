// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/eval/eval/tensor_nodes.h>

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
        .add("a", spec(1))
        .add("x3", spec({x(3)}, N()))
        .add("x3f", spec(float_cells({x(3)}), N()));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_dynamic(const vespalib::string &expr, const vespalib::string &expect) {
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expect, param_repo));
    auto info = fixture.find_all<Lambda>();
    EXPECT_EQUAL(info.size(), 1u);
}

void verify_const(const vespalib::string &expr, const vespalib::string &expect) {
    EvalFixture fixture(prod_engine, expr, param_repo, true);
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQUAL(fixture.result(), EvalFixture::ref(expect, param_repo));
    auto info = fixture.find_all<ConstValue>();
    EXPECT_EQUAL(info.size(), 1u);
}

TEST("require that simple constant tensor lambda works") {
    TEST_DO(verify_const("tensor(x[3])(x+1)", "tensor(x[3]):[1,2,3]"));
}

TEST("require that simple dynamic tensor lambda works") {
    TEST_DO(verify_dynamic("tensor(x[3])(x+a)", "tensor(x[3]):[1,2,3]"));
}

TEST("require that tensor lambda can be used for tensor slicing") {
    TEST_DO(verify_dynamic("tensor(x[2])(x3{x:(x+a)})", "tensor(x[2]):[2,3]"));
    TEST_DO(verify_dynamic("tensor(x[2])(a+x3{x:(x)})", "tensor(x[2]):[2,3]"));
}

TEST("require that tensor lambda can be used for tensor casting") {
    TEST_DO(verify_dynamic("tensor(x[3])(x3f{x:(x)})", "tensor(x[3]):[1,2,3]"));
    TEST_DO(verify_dynamic("tensor<float>(x[3])(x3{x:(x)})", "tensor<float>(x[3]):[1,2,3]"));
}

TEST("require that constant nested tensor lambda using tensor peek works") {
    TEST_DO(verify_const("tensor(x[2])(tensor(y[2])((x+y)+1){y:(x)})", "tensor(x[2]):[1,3]"));
}

TEST("require that dynamic nested tensor lambda using tensor peek works") {
    TEST_DO(verify_dynamic("tensor(x[2])(tensor(y[2])((x+y)+a){y:(x)})", "tensor(x[2]):[1,3]"));
}

TEST("require that non-double result from inner tensor lambda function fails type resolving") {
    auto fun_a = Function::parse("tensor(x[2])(a)");
    auto fun_b = Function::parse("tensor(x[2])(a{y:(x)})");
    NodeTypes types_ad(*fun_a, {ValueType::from_spec("double")});
    NodeTypes types_at(*fun_a, {ValueType::from_spec("tensor(y[2])")});
    NodeTypes types_bd(*fun_b, {ValueType::from_spec("double")});
    NodeTypes types_bt(*fun_b, {ValueType::from_spec("tensor(y[2])")});
    EXPECT_EQUAL(types_ad.get_type(fun_a->root()).to_spec(), "tensor(x[2])");
    EXPECT_EQUAL(types_at.get_type(fun_a->root()).to_spec(), "error");
    EXPECT_EQUAL(types_bd.get_type(fun_b->root()).to_spec(), "error");
    EXPECT_EQUAL(types_bt.get_type(fun_b->root()).to_spec(), "tensor(x[2])");
}

TEST("require that type resolving also include nodes in the inner tensor lambda function") {
    auto fun = Function::parse("tensor(x[2])(a)");
    NodeTypes types(*fun, {ValueType::from_spec("double")});
    auto lambda = nodes::as<nodes::TensorLambda>(fun->root());
    ASSERT_TRUE(lambda != nullptr);
    EXPECT_EQUAL(types.get_type(*lambda).to_spec(), "tensor(x[2])");
    auto symbol = nodes::as<nodes::Symbol>(lambda->lambda().root());
    ASSERT_TRUE(symbol != nullptr);
    EXPECT_EQUAL(types.get_type(*symbol).to_spec(), "double");
}

TEST_MAIN() { TEST_RUN_ALL(); }
