// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/dense_simple_map_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/tensor_model.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;
using namespace vespalib::tensor;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("a", spec(1.5))
        .add("b", spec(2.5))
        .add("sparse", spec({x({"a"})}, N()))
        .add("mixed", spec({x({"a"}),y(5)}, N()))
        .add_matrix("x", 5, "y", 3);
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, bool inplace) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseSimpleMapFunction>();
    ASSERT_EQ(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQ(info[0]->inplace(), inplace);
    ASSERT_EQ(fixture.num_params(), 1);
    if (inplace) {
        EXPECT_EQ(fixture.get_param(0), fixture.result());
    } else {
        EXPECT_TRUE(!(fixture.get_param(0) == fixture.result()));
    }
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<DenseSimpleMapFunction>();
    EXPECT_TRUE(info.empty());
}

TEST(MapTest, dense_map_is_optimized) {
    verify_optimized("map(x5y3,f(x)(x+10))", false);
    verify_optimized("map(x5y3f,f(x)(x+10))", false);
}

TEST(MapTest, simple_dense_map_can_be_inplace) {
    verify_optimized("map(@x5y3,f(x)(x+10))", true);
    verify_optimized("map(@x5y3f,f(x)(x+10))", true);
}

TEST(MapTest, scalar_map_is_not_optimized) {
    verify_not_optimized("map(a,f(x)(x+10))");
}

TEST(MapTest, sparse_map_is_not_optimized) {
    verify_not_optimized("map(sparse,f(x)(x+10))");
}

TEST(MapTest, mixed_map_is_not_optimized) {
    verify_not_optimized("map(mixed,f(x)(x+10))");
}

GTEST_MAIN_RUN_ALL_TESTS()
