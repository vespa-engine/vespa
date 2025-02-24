// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/vector_from_doubles_function.h>
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
        .add("c", GenSpec(3.0))
        .add("d", GenSpec(4.0))
        .add("x5", GenSpec().idx("x", 5));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify(const std::string &expr, size_t expect_optimized_cnt, size_t expect_not_optimized_cnt) {
    SCOPED_TRACE(expr);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    auto info = fixture.find_all<VectorFromDoublesFunction>();
    EXPECT_EQ(info.size(), expect_optimized_cnt);
    for (size_t i = 0; i < info.size(); ++i) {
        EXPECT_TRUE(info[i]->result_is_mutable());
    }
    EXPECT_EQ(fixture.find_all<Concat>().size(), expect_not_optimized_cnt);
}

//-----------------------------------------------------------------------------

TEST(VectorFromDoublesFunctionTest, require_that_multiple_concats_are_optimized)
{
    verify("concat(a,b,x)", 1, 0);
    verify("concat(a,concat(b,concat(c,d,x),x),x)", 1, 0);
    verify("concat(concat(concat(a,b,x),c,x),d,x)", 1, 0);
    verify("concat(concat(a,b,x),concat(c,d,x),x)", 1, 0);
}

TEST(VectorFromDoublesFunctionTest, require_that_concat_along_different_dimension_is_not_optimized)
{
    verify("concat(concat(a,b,x),concat(c,d,x),y)", 2, 1);
}

TEST(VectorFromDoublesFunctionTest, require_that_concat_of_vector_and_double_is_not_optimized)
{
    verify("concat(a,x5,x)", 0, 1);
    verify("concat(x5,b,x)", 0, 1);
}

GTEST_MAIN_RUN_ALL_TESTS()
