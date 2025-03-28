// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/simple_value.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/instruction/replace_type_function.h>
#include <vespa/eval/instruction/dense_cell_range_function.h>
#include <vespa/eval/instruction/dense_lambda_peek_function.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/tensor_nodes.h>
#include <vespa/vespalib/gtest/gtest.h>

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
        .add("x3_float", GenSpec().idx("x", 3).cells(CellType::FLOAT))
        .add("x3_bfloat16", GenSpec().idx("x", 3).cells(CellType::BFLOAT16))
        .add("x3_int8", GenSpec().idx("x", 3).cells(CellType::INT8))
        .add("x3m", GenSpec().map("x", {"0", "1", "2"}))
        .add("x3y5", GenSpec().idx("x", 3).idx("y", 5))
        .add("x3y5_float", GenSpec().idx("x", 3).idx("y", 5).cells(CellType::FLOAT))
        .add("x3y5_bfloat16", GenSpec().idx("x", 3).idx("y", 5).cells(CellType::BFLOAT16))
        .add("x3y5_int8", GenSpec().idx("x", 3).idx("y", 5).cells(CellType::INT8))
        .add("x15", GenSpec().idx("x", 15))
        .add("x15_float", GenSpec().idx("x", 15).cells(CellType::FLOAT))
        .add("x15_bfloat16", GenSpec().idx("x", 15).cells(CellType::BFLOAT16))
        .add("x15_int8", GenSpec().idx("x", 15).cells(CellType::INT8));
}
EvalFixture::ParamRepo param_repo = make_params();

template <typename T, typename F>
void verify_impl(const std::string &expr, const std::string &expect, F &&inspect) {
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture simple_factory_fixture(simple_factory, expr, param_repo, false);
    auto expect_spec = EvalFixture::ref(expect, param_repo);
    EXPECT_EQ(fixture.result(), expect_spec);
    EXPECT_EQ(slow_fixture.result(), expect_spec);
    EXPECT_EQ(simple_factory_fixture.result(), expect_spec);
    EXPECT_EQ(EvalFixture::ref(expr, param_repo), expect_spec);
    auto info = fixture.find_all<T>();
    ASSERT_EQ(info.size(), 1u);
    inspect(info[0]);
}
template <typename T>
void verify_impl(const std::string &expr, const std::string &expect) {
    verify_impl<T>(expr, expect, [](const T*){});
}

void verify_generic(const std::string &expr, const std::string &expect) {
    SCOPED_TRACE(expr);
    verify_impl<tensor_function::Lambda>(expr, expect);
}

void verify_reshape(const std::string &expr, const std::string &expect) {
    SCOPED_TRACE(expr);
    verify_impl<ReplaceTypeFunction>(expr, expect);
}

void verify_range(const std::string &expr, const std::string &expect) {
    SCOPED_TRACE(expr);
    verify_impl<DenseCellRangeFunction>(expr, expect);
}

void verify_idx_fun(const std::string &expr, const std::string &expect,
                    const std::string &expect_idx_fun)
{
    SCOPED_TRACE(expr);
    verify_impl<DenseLambdaPeekFunction>(expr, expect,
                                         [&](const DenseLambdaPeekFunction *info)
                                         {
                                             EXPECT_EQ(info->idx_fun_dump(), expect_idx_fun);
                                         });
}

void verify_const(const std::string &expr, const std::string &expect) {
    verify_impl<ConstValue>(expr, expect);
}

//-----------------------------------------------------------------------------

TEST(TensorLambdaTest, require_that_simple_constant_tensor_lambda_works)
{
    verify_const("tensor(x[3])(x+1)", "tensor(x[3]):[1,2,3]");
}

TEST(TensorLambdaTest, require_that_tensor_lambda_can_be_used_for_cell_type_casting)
{
    verify_idx_fun("tensor(x[3])(x3_float{x:(x)})", "tensor(x[3]):[1,2,3]", "f(x)(x)");
    verify_idx_fun("tensor(x[3])(x3_bfloat16{x:(x)})", "tensor(x[3]):[1,2,3]", "f(x)(x)");
    verify_idx_fun("tensor(x[3])(x3_int8{x:(x)})", "tensor(x[3]):[1,2,3]", "f(x)(x)");
    verify_idx_fun("tensor<float>(x[3])(x3{x:(x)})", "tensor<float>(x[3]):[1,2,3]", "f(x)(x)");
    verify_idx_fun("tensor<bfloat16>(x[3])(x3{x:(x)})", "tensor<bfloat16>(x[3]):[1,2,3]", "f(x)(x)");
    verify_idx_fun("tensor<int8>(x[3])(x3{x:(x)})", "tensor<int8>(x[3]):[1,2,3]", "f(x)(x)");
}

TEST(TensorLambdaTest, require_that_constant_nested_tensor_lambda_using_tensor_peek_works)
{
    verify_const("tensor(x[2])(tensor(y[2])((x+y)+1){y:(x)})", "tensor(x[2]):[1,3]");
}

TEST(TensorLambdaTest, require_that_tensor_reshape_is_optimized)
{
    verify_reshape("tensor(x[15])(x3y5{x:(x/5),y:(x%5)})", "x15");
    verify_reshape("tensor(x[3],y[5])(x15{x:(x*5+y)})", "x3y5");
    verify_reshape("tensor<float>(x[15])(x3y5_float{x:(x/5),y:(x%5)})", "x15_float");
    verify_reshape("tensor<bfloat16>(x[15])(x3y5_bfloat16{x:(x/5),y:(x%5)})", "x15_bfloat16");
    verify_reshape("tensor<int8>(x[15])(x3y5_int8{x:(x/5),y:(x%5)})", "x15_int8");
}

TEST(TensorLambdaTest, require_that_tensor_reshape_with_non_matching_cell_type_requires_cell_copy)
{
    verify_idx_fun("tensor(x[15])(x3y5_float{x:(x/5),y:(x%5)})", "x15", "f(x)((floor((x/5))*5)+(x%5))");
    verify_idx_fun("tensor<float>(x[15])(x3y5{x:(x/5),y:(x%5)})", "x15_float", "f(x)((floor((x/5))*5)+(x%5))");
    verify_idx_fun("tensor(x[3],y[5])(x15_float{x:(x*5+y)})", "x3y5", "f(x,y)((x*5)+y)");
    verify_idx_fun("tensor<float>(x[3],y[5])(x15{x:(x*5+y)})", "x3y5_float", "f(x,y)((x*5)+y)");
    verify_idx_fun("tensor<bfloat16>(x[3],y[5])(x15{x:(x*5+y)})", "x3y5_bfloat16", "f(x,y)((x*5)+y)");
    verify_idx_fun("tensor<int8>(x[3],y[5])(x15{x:(x*5+y)})", "x3y5_int8", "f(x,y)((x*5)+y)");
}

TEST(TensorLambdaTest, require_that_tensor_cell_subrange_view_is_optimized)
{
    verify_range("tensor(y[5])(x3y5{x:1,y:(y)})", "x3y5{x:1}");
    verify_range("tensor(x[3])(x15{x:(x+5)})", "tensor(x[3]):[6,7,8]");
    verify_range("tensor<float>(y[5])(x3y5_float{x:1,y:(y)})", "x3y5_float{x:1}");
    verify_range("tensor<float>(x[3])(x15_float{x:(x+5)})", "tensor<float>(x[3]):[6,7,8]");
    verify_range("tensor<float>(x[3])(x15_float{x:(x+5)})", "tensor<float>(x[3]):[6,7,8]");
    verify_range("tensor<bfloat16>(x[3])(x15_bfloat16{x:(x+5)})", "tensor<bfloat16>(x[3]):[6,7,8]");
    verify_range("tensor<int8>(x[3])(x15_int8{x:(x+5)})", "tensor<int8>(x[3]):[6,7,8]");
}

TEST(TensorLambdaTest, require_that_tensor_cell_subrange_with_non_matching_cell_type_requires_cell_copy)
{
    verify_idx_fun("tensor(x[3])(x15_float{x:(x+5)})", "tensor(x[3]):[6,7,8]", "f(x)(x+5)");
    verify_idx_fun("tensor<float>(x[3])(x15{x:(x+5)})", "tensor<float>(x[3]):[6,7,8]", "f(x)(x+5)");
    verify_idx_fun("tensor<bfloat16>(x[3])(x15{x:(x+5)})", "tensor<bfloat16>(x[3]):[6,7,8]", "f(x)(x+5)");
    verify_idx_fun("tensor<int8>(x[3])(x15{x:(x+5)})", "tensor<int8>(x[3]):[6,7,8]", "f(x)(x+5)");
}

TEST(TensorLambdaTest, require_that_non_continuous_cell_extraction_is_optimized)
{
    verify_idx_fun("tensor(x[3])(x3y5{x:(x),y:2})", "x3y5{y:2}", "f(x)((floor(x)*5)+2)");
    verify_idx_fun("tensor(x[3])(x3y5_float{x:(x),y:2})", "x3y5{y:2}", "f(x)((floor(x)*5)+2)");
    verify_idx_fun("tensor<float>(x[3])(x3y5{x:(x),y:2})", "x3y5_float{y:2}", "f(x)((floor(x)*5)+2)");
    verify_idx_fun("tensor<float>(x[3])(x3y5_float{x:(x),y:2})", "x3y5_float{y:2}", "f(x)((floor(x)*5)+2)");
    verify_idx_fun("tensor<bfloat16>(x[3])(x3y5_bfloat16{x:(x),y:2})", "x3y5_bfloat16{y:2}", "f(x)((floor(x)*5)+2)");
    verify_idx_fun("tensor<int8>(x[3])(x3y5_int8{x:(x),y:2})", "x3y5_int8{y:2}", "f(x)((floor(x)*5)+2)");
}

TEST(TensorLambdaTest, require_that_simple_dynamic_tensor_lambda_works)
{
    verify_generic("tensor(x[3])(x+a)", "tensor(x[3]):[1,2,3]");
    verify_generic("tensor<float>(x[3])(x+a)", "tensor<float>(x[3]):[1,2,3]");
    verify_generic("tensor<bfloat16>(x[3])(x+a)", "tensor<bfloat16>(x[3]):[1,2,3]");
    verify_generic("tensor<int8>(x[3])(x+a)", "tensor<int8>(x[3]):[1,2,3]");
}

TEST(TensorLambdaTest, require_that_compiled_multi_dimensional_multi_param_dynamic_tensor_lambda_works)
{
    verify_generic("tensor(x[3],y[2])((b-a)+x+y)", "tensor(x[3],y[2]):[[1,2],[2,3],[3,4]]");
    verify_generic("tensor<float>(x[3],y[2])((b-a)+x+y)", "tensor<float>(x[3],y[2]):[[1,2],[2,3],[3,4]]");
}

TEST(TensorLambdaTest, require_that_interpreted_multi_dimensional_multi_param_dynamic_tensor_lambda_works)
{
    verify_generic("tensor(x[3],y[2])((x3{x:(a)}-a)+x+y)", "tensor(x[3],y[2]):[[1,2],[2,3],[3,4]]");
    verify_generic("tensor<float>(x[3],y[2])((x3{x:(a)}-a)+x+y)", "tensor<float>(x[3],y[2]):[[1,2],[2,3],[3,4]]");
}

TEST(TensorLambdaTest, require_that_tensor_lambda_can_be_used_for_tensor_slicing)
{
    verify_generic("tensor(x[2])(x3{x:(x+a)})", "tensor(x[2]):[2,3]");
    verify_generic("tensor(x[2])(a+x3{x:(x)})", "tensor(x[2]):[2,3]");
}

TEST(TensorLambdaTest, require_that_tensor_lambda_can_be_used_to_convert_from_sparse_to_dense_tensors)
{
    verify_generic("tensor(x[3])(x3m{x:(x)})", "tensor(x[3]):[1,2,3]");
    verify_generic("tensor(x[2])(x3m{x:(x)})", "tensor(x[2]):[1,2]");
}

TEST(TensorLambdaTest, require_that_dynamic_nested_tensor_lambda_using_tensor_peek_works)
{
    verify_generic("tensor(x[2])(tensor(y[2])((x+y)+a){y:(x)})", "tensor(x[2]):[1,3]");
}

TEST(TensorLambdaTest, require_that_out_of_bounds_cell_extraction_is_not_optimized)
{
    verify_generic("tensor(x[3])(x3y5{x:1,y:(x+3)})", "tensor(x[3]):[9,10,0]");
    verify_generic("tensor(x[3])(x3y5{x:1,y:(x-1)})", "tensor(x[3]):[0,6,7]");
    verify_generic("tensor(x[3])(x3y5{x:(x+1),y:(x)})", "tensor(x[3]):[6,12,0]");
    verify_generic("tensor(x[3])(x3y5{x:(x-1),y:(x)})", "tensor(x[3]):[0,2,8]");
}

TEST(TensorLambdaTest, require_that_non_double_result_from_inner_tensor_lambda_function_fails_type_resolving)
{
    auto fun_a = Function::parse("tensor(x[2])(a)");
    auto fun_b = Function::parse("tensor(x[2])(a{y:(x)})");
    NodeTypes types_ad(*fun_a, {ValueType::from_spec("double")});
    NodeTypes types_at(*fun_a, {ValueType::from_spec("tensor(y[2])")});
    NodeTypes types_bd(*fun_b, {ValueType::from_spec("double")});
    NodeTypes types_bt(*fun_b, {ValueType::from_spec("tensor(y[2])")});
    EXPECT_EQ(types_ad.get_type(fun_a->root()).to_spec(), "tensor(x[2])");
    EXPECT_EQ(types_at.get_type(fun_a->root()).to_spec(), "error");
    EXPECT_EQ(types_bd.get_type(fun_b->root()).to_spec(), "error");
    EXPECT_EQ(types_bt.get_type(fun_b->root()).to_spec(), "tensor(x[2])");
}

TEST(TensorLambdaTest, require_that_type_resolving_also_include_nodes_in_the_inner_tensor_lambda_function)
{
    auto fun = Function::parse("tensor(x[2])(a)");
    NodeTypes types(*fun, {ValueType::from_spec("double")});
    auto lambda = nodes::as<nodes::TensorLambda>(fun->root());
    ASSERT_TRUE(lambda != nullptr);
    EXPECT_EQ(types.get_type(*lambda).to_spec(), "tensor(x[2])");
    auto symbol = nodes::as<nodes::Symbol>(lambda->lambda().root());
    ASSERT_TRUE(symbol != nullptr);
    EXPECT_EQ(types.get_type(*symbol).to_spec(), "double");
}

size_t count_nodes(const NodeTypes &types) {
    size_t cnt = 0;
    types.each([&](const auto &, const auto &){++cnt;});
    return cnt;
}

TEST(TensorLambdaTest, require_that_type_exporting_also_include_nodes_in_the_inner_tensor_lambda_function)
{
    auto fun = Function::parse("tensor(x[2])(tensor(y[2])((x+y)+a){y:(x)})");
    NodeTypes types(*fun, {ValueType::from_spec("double")});
    const auto &root = fun->root();
    NodeTypes copy = types.export_types(root);
    EXPECT_TRUE(copy.errors().empty());
    EXPECT_EQ(count_nodes(types), count_nodes(copy));

    auto lambda = nodes::as<nodes::TensorLambda>(root);
    ASSERT_TRUE(lambda != nullptr);
    NodeTypes outer = copy.export_types(lambda->lambda().root());
    EXPECT_TRUE(outer.errors().empty());

    auto inner_lambda = nodes::as<nodes::TensorLambda>(lambda->lambda().root().get_child(0));
    ASSERT_TRUE(inner_lambda != nullptr);
    NodeTypes inner = outer.export_types(inner_lambda->lambda().root());
    EXPECT_TRUE(inner.errors().empty());
    // [x, y, (x+y), a, (x+y)+a] are the 5 nodes:
    EXPECT_EQ(count_nodes(inner), 5u);
}

GTEST_MAIN_RUN_ALL_TESTS()
