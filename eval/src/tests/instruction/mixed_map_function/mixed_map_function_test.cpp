// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/instruction/mixed_map_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;
using namespace vespalib::eval::tensor_function;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("a", GenSpec(1.5))
        .add_mutable("@a", GenSpec(1.5))
        .add("b", GenSpec(2.5))
        .add_variants("sparse", GenSpec().map("x", {"a"}))
        .add_variants("mixed", GenSpec().map("x", {"a"}).idx("y", 5))
        .add_variants("x5y3", GenSpec().idx("x", 5).idx("y", 3));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<MixedMapFunction>();
    ASSERT_EQ(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQ(info[0]->inplace(), true);
    ASSERT_EQ(fixture.num_params(), 1);
    // inplace check:
    EXPECT_EQ(fixture.get_param(0), fixture.result());
}

void verify_not_optimized(const vespalib::string &expr) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<MixedMapFunction>();
    EXPECT_TRUE(info.empty());
    EXPECT_TRUE(!(fixture.get_param(0) == fixture.result()));
}

TEST(MapTest, dense_map_can_be_optimized) {
    verify_not_optimized("map(x5y3,f(x)(x+10))");
    verify_not_optimized("map(x5y3_f,f(x)(x+10))");
    verify_optimized("map(@x5y3,f(x)(x+10))");
    verify_optimized("map(@x5y3_f,f(x)(x+10))");
}

TEST(MapTest, scalar_map_is_not_optimized) {
    verify_not_optimized("map(a,f(x)(x+10))");
    verify_not_optimized("map(@a,f(x)(x+10))");
}

TEST(MapTest, sparse_map_can_be_optimized) {
    verify_not_optimized("map(sparse,f(x)(x+10))");
    verify_optimized("map(@sparse,f(x)(x+10))");
}

TEST(MapTest, mixed_map_can_be_optimized) {
    verify_not_optimized("map(mixed,f(x)(x+10))");
    verify_optimized("map(@mixed,f(x)(x+10))");
}

GTEST_MAIN_RUN_ALL_TESTS()
