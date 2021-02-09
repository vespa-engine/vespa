// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/instruction/replace_type_function.h>
#include <vespa/eval/instruction/dense_cell_range_function.h>
#include <vespa/eval/instruction/dense_lambda_peek_function.h>
#include <vespa/eval/instruction/fast_rename_optimizer.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/tensor_nodes.h>

#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &simple_factory = SimpleValueBuilderFactory::get();
const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("a", GenSpec(1))
        .add("b", GenSpec(2))
        .add("x3", GenSpec().idx("x", 3))
        .add("x3f", GenSpec().idx("x", 3).cells_float())
        .add("x3m", GenSpec().map("x", 3))
        .add("x3y5", GenSpec().idx("x", 3).idx("y", 5))
        .add("x3y5f", GenSpec().idx("x", 3).idx("y", 5).cells_float())
        .add("x15", GenSpec().idx("x", 15))
        .add("x15f", GenSpec().idx("x", 15).cells_float());
}
EvalFixture::ParamRepo param_repo = make_params();

template <typename T, typename F>
void verify_impl(const vespalib::string &expr, const vespalib::string &expect, F &&inspect) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture simple_factory_fixture(simple_factory, expr, param_repo, false);
    auto expect_spec = EvalFixture::ref(expect, param_repo);
    EXPECT_EQUAL(fixture.result(), expect_spec);
    EXPECT_EQUAL(slow_fixture.result(), expect_spec);
    EXPECT_EQUAL(simple_factory_fixture.result(), expect_spec);
    EXPECT_EQUAL(EvalFixture::ref(expr, param_repo), expect_spec);
    auto info = fixture.find_all<T>();
    if (EXPECT_EQUAL(info.size(), 1u)) {
        inspect(info[0]);
    }
}
template <typename T>
void verify_impl(const vespalib::string &expr, const vespalib::string &expect) {
    verify_impl<T>(expr, expect, [](const T*){});
}

void verify_generic(const vespalib::string &expr, const vespalib::string &expect) {
    verify_impl<tensor_function::Lambda>(expr, expect);
}

void verify_reshape(const vespalib::string &expr, const vespalib::string &expect) {
    verify_impl<ReplaceTypeFunction>(expr, expect);
}

void verify_range(const vespalib::string &expr, const vespalib::string &expect) {
    verify_impl<DenseCellRangeFunction>(expr, expect);
}

void verify_idx_fun(const vespalib::string &expr, const vespalib::string &expect,
                    const vespalib::string &expect_idx_fun)
{
    verify_impl<DenseLambdaPeekFunction>(expr, expect,
                                         [&](const DenseLambdaPeekFunction *info)
                                         {
                                             EXPECT_EQUAL(info->idx_fun_dump(), expect_idx_fun);
                                         });
}

void verify_const(const vespalib::string &expr, const vespalib::string &expect) {
    verify_impl<ConstValue>(expr, expect);
}

//-----------------------------------------------------------------------------

TEST("require that simple constant tensor lambda works") {
    TEST_DO(verify_const("tensor(x[3])(x+1)", "tensor(x[3]):[1,2,3]"));
}

TEST("require that tensor lambda can be used for cell type casting") {
    TEST_DO(verify_idx_fun("tensor(x[3])(x3f{x:(x)})", "tensor(x[3]):[1,2,3]", "f(x)(x)"));
    TEST_DO(verify_idx_fun("tensor<float>(x[3])(x3{x:(x)})", "tensor<float>(x[3]):[1,2,3]", "f(x)(x)"));
}

TEST("require that constant nested tensor lambda using tensor peek works") {
    TEST_DO(verify_const("tensor(x[2])(tensor(y[2])((x+y)+1){y:(x)})", "tensor(x[2]):[1,3]"));
}

TEST("require that tensor reshape is optimized") {
    TEST_DO(verify_reshape("tensor(x[15])(x3y5{x:(x/5),y:(x%5)})", "x15"));
    TEST_DO(verify_reshape("tensor(x[3],y[5])(x15{x:(x*5+y)})", "x3y5"));
    TEST_DO(verify_reshape("tensor<float>(x[15])(x3y5f{x:(x/5),y:(x%5)})", "x15f"));
}

TEST("require that tensor reshape with non-matching cell type requires cell copy") {
    TEST_DO(verify_idx_fun("tensor(x[15])(x3y5f{x:(x/5),y:(x%5)})", "x15", "f(x)((floor((x/5))*5)+(x%5))"));
    TEST_DO(verify_idx_fun("tensor<float>(x[15])(x3y5{x:(x/5),y:(x%5)})", "x15f", "f(x)((floor((x/5))*5)+(x%5))"));
    TEST_DO(verify_idx_fun("tensor(x[3],y[5])(x15f{x:(x*5+y)})", "x3y5", "f(x,y)((x*5)+y)"));
    TEST_DO(verify_idx_fun("tensor<float>(x[3],y[5])(x15{x:(x*5+y)})", "x3y5f", "f(x,y)((x*5)+y)"));
}

TEST("require that tensor cell subrange view is optimized") {
    TEST_DO(verify_range("tensor(y[5])(x3y5{x:1,y:(y)})", "x3y5{x:1}"));
    TEST_DO(verify_range("tensor(x[3])(x15{x:(x+5)})", "tensor(x[3]):[6,7,8]"));
    TEST_DO(verify_range("tensor<float>(y[5])(x3y5f{x:1,y:(y)})", "x3y5f{x:1}"));
    TEST_DO(verify_range("tensor<float>(x[3])(x15f{x:(x+5)})", "tensor<float>(x[3]):[6,7,8]"));
}

TEST("require that tensor cell subrange with non-matching cell type requires cell copy") {
    TEST_DO(verify_idx_fun("tensor(x[3])(x15f{x:(x+5)})", "tensor(x[3]):[6,7,8]", "f(x)(x+5)"));
    TEST_DO(verify_idx_fun("tensor<float>(x[3])(x15{x:(x+5)})", "tensor<float>(x[3]):[6,7,8]", "f(x)(x+5)"));
}

TEST("require that non-continuous cell extraction is optimized") {
    TEST_DO(verify_idx_fun("tensor(x[3])(x3y5{x:(x),y:2})", "x3y5{y:2}", "f(x)((floor(x)*5)+2)"));
    TEST_DO(verify_idx_fun("tensor(x[3])(x3y5f{x:(x),y:2})", "x3y5{y:2}", "f(x)((floor(x)*5)+2)"));
    TEST_DO(verify_idx_fun("tensor<float>(x[3])(x3y5{x:(x),y:2})", "x3y5f{y:2}", "f(x)((floor(x)*5)+2)"));
    TEST_DO(verify_idx_fun("tensor<float>(x[3])(x3y5f{x:(x),y:2})", "x3y5f{y:2}", "f(x)((floor(x)*5)+2)"));
}

TEST("require that simple dynamic tensor lambda works") {
    TEST_DO(verify_generic("tensor(x[3])(x+a)", "tensor(x[3]):[1,2,3]"));
}

TEST("require that compiled multi-dimensional multi-param dynamic tensor lambda works") {
    TEST_DO(verify_generic("tensor(x[3],y[2])((b-a)+x+y)", "tensor(x[3],y[2]):[[1,2],[2,3],[3,4]]"));
    TEST_DO(verify_generic("tensor<float>(x[3],y[2])((b-a)+x+y)", "tensor<float>(x[3],y[2]):[[1,2],[2,3],[3,4]]"));
}

TEST("require that interpreted multi-dimensional multi-param dynamic tensor lambda works") {
    TEST_DO(verify_generic("tensor(x[3],y[2])((x3{x:(a)}-a)+x+y)", "tensor(x[3],y[2]):[[1,2],[2,3],[3,4]]"));
    TEST_DO(verify_generic("tensor<float>(x[3],y[2])((x3{x:(a)}-a)+x+y)", "tensor<float>(x[3],y[2]):[[1,2],[2,3],[3,4]]"));
}

TEST("require that tensor lambda can be used for tensor slicing") {
    TEST_DO(verify_generic("tensor(x[2])(x3{x:(x+a)})", "tensor(x[2]):[2,3]"));
    TEST_DO(verify_generic("tensor(x[2])(a+x3{x:(x)})", "tensor(x[2]):[2,3]"));
}

TEST("require that tensor lambda can be used to convert from sparse to dense tensors") {
    TEST_DO(verify_generic("tensor(x[3])(x3m{x:(x)})", "tensor(x[3]):[1,2,3]"));
    TEST_DO(verify_generic("tensor(x[2])(x3m{x:(x)})", "tensor(x[2]):[1,2]"));
}

TEST("require that dynamic nested tensor lambda using tensor peek works") {
    TEST_DO(verify_generic("tensor(x[2])(tensor(y[2])((x+y)+a){y:(x)})", "tensor(x[2]):[1,3]"));
}

TEST("require that out-of-bounds cell extraction is not optimized") {
    TEST_DO(verify_generic("tensor(x[3])(x3y5{x:1,y:(x+3)})", "tensor(x[3]):[9,10,0]"));
    TEST_DO(verify_generic("tensor(x[3])(x3y5{x:1,y:(x-1)})", "tensor(x[3]):[0,6,7]"));
    TEST_DO(verify_generic("tensor(x[3])(x3y5{x:(x+1),y:(x)})", "tensor(x[3]):[6,12,0]"));
    TEST_DO(verify_generic("tensor(x[3])(x3y5{x:(x-1),y:(x)})", "tensor(x[3]):[0,2,8]"));
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

size_t count_nodes(const NodeTypes &types) {
    size_t cnt = 0;
    types.each([&](const auto &, const auto &){++cnt;});
    return cnt;
}

TEST("require that type exporting also include nodes in the inner tensor lambda function") {
    auto fun = Function::parse("tensor(x[2])(tensor(y[2])((x+y)+a){y:(x)})");
    NodeTypes types(*fun, {ValueType::from_spec("double")});
    const auto &root = fun->root();
    NodeTypes copy = types.export_types(root);
    EXPECT_TRUE(copy.errors().empty());
    EXPECT_EQUAL(count_nodes(types), count_nodes(copy));

    auto lambda = nodes::as<nodes::TensorLambda>(root);
    ASSERT_TRUE(lambda != nullptr);
    NodeTypes outer = copy.export_types(lambda->lambda().root());
    EXPECT_TRUE(outer.errors().empty());

    auto inner_lambda = nodes::as<nodes::TensorLambda>(lambda->lambda().root().get_child(0));
    ASSERT_TRUE(inner_lambda != nullptr);
    NodeTypes inner = outer.export_types(inner_lambda->lambda().root());
    EXPECT_TRUE(inner.errors().empty());
    // [x, y, (x+y), a, (x+y)+a] are the 5 nodes:
    EXPECT_EQUAL(count_nodes(inner), 5u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
