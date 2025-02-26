// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/dense_tensor_create_function.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("a", GenSpec(1.0))
        .add("b", GenSpec(2.0))
        .add("c", GenSpec(3.0));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify(const std::string &expr, size_t expect_optimized_cnt, size_t expect_not_optimized_cnt) {
    SCOPED_TRACE(expr);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<DenseTensorCreateFunction>();
    EXPECT_EQ(info.size(), expect_optimized_cnt);
    for (size_t i = 0; i < info.size(); ++i) {
        EXPECT_TRUE(info[i]->result_is_mutable());
    }
    EXPECT_EQ(fixture.find_all<Create>().size(), expect_not_optimized_cnt);
}

//-----------------------------------------------------------------------------

TEST(DenseTensorCreateFunctionTest, require_that_tensor_create_can_be_optimized)
{
    verify("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}", 0, 0); // NB: const value
    verify("tensor(x[3]):{{x:0}:a,{x:1}:b,{x:2}:c}", 1, 0);
    verify("tensor<float>(x[3]):{{x:0}:a,{x:1}:b,{x:2}:c}", 1, 0);
    verify("tensor(x[3]):{{x:0}:a+b,{x:1}:b-c,{x:2}:c*a}", 1, 0);
}

TEST(DenseTensorCreateFunctionTest, require_that_tensor_create_can_be_optimized_with_missing_cells_padded_with_zero)
{
    verify("tensor(x[3],y[5]):{{x:0,y:1}:a,{x:1,y:3}:b,{x:2,y:4}:c}", 1, 0);
}

TEST(DenseTensorCreateFunctionTest, require_that_tensor_create_in_not_optimized_for_sparse_tensor)
{
    verify("tensor(x{}):{{x:0}:a,{x:1}:b,{x:2}:c}", 0, 1);
}

TEST(DenseTensorCreateFunctionTest, require_that_tensor_create_in_not_optimized_for_mixed_tensor)
{
    verify("tensor(x{},y[3]):{{x:a,y:0}:a,{x:a,y:1}:b,{x:a,y:2}:c}", 0, 1);
}

GTEST_MAIN_RUN_ALL_TESTS()
