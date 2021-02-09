// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/fast_value.h>
#include <vespa/eval/eval/tensor_function.h>
#include <vespa/eval/eval/test/eval_fixture.h>
#include <vespa/eval/eval/test/gen_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval::operation;
using namespace vespalib::eval::tensor_function;
using namespace vespalib::eval::test;
using namespace vespalib::eval;
//using namespace vespalib;

const ValueBuilderFactory &prod_factory = FastValueBuilderFactory::get();

EvalFixture::ParamRepo make_params() {
    return EvalFixture::ParamRepo()
        .add("a", GenSpec(1.5))
        .add("b", GenSpec(2.5))
        .add("sparse", GenSpec().map("x", {"a","b"}))
        .add("mixed", GenSpec().map("x", {"a"}).idx("y", 5))
        .add_variants("x5y3", GenSpec().idx("x", 5).idx("y", 3));
}
EvalFixture::ParamRepo param_repo = make_params();

void verify_optimized(const vespalib::string &expr, op1_t op1, bool inplace = false) {
    EvalFixture slow_fixture(prod_factory, expr, param_repo, false);
    EvalFixture fixture(prod_factory, expr, param_repo, true, true);
    EXPECT_EQ(fixture.result(), EvalFixture::ref(expr, param_repo));
    EXPECT_EQ(fixture.result(), slow_fixture.result());
    auto info = fixture.find_all<tensor_function::Map>();
    ASSERT_EQ(info.size(), 1u);
    EXPECT_TRUE(info[0]->result_is_mutable());
    EXPECT_EQ(info[0]->function(), op1);
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
    auto info = fixture.find_all<Map>();
    EXPECT_TRUE(info.empty());
}

TEST(PowAsMapTest, squared_dense_tensor_is_optimized) {
    verify_optimized("x5y3^2.0", Square::f);
    verify_optimized("pow(x5y3,2.0)", Square::f);
    verify_optimized("join(x5y3,2.0,f(x,y)(x^y))", Square::f);
    verify_optimized("join(x5y3,2.0,f(x,y)(pow(x,y)))", Square::f);
    verify_optimized("join(x5y3_f,2.0,f(x,y)(pow(x,y)))", Square::f);
    verify_optimized("join(@x5y3,2.0,f(x,y)(pow(x,y)))", Square::f, true);
    verify_optimized("join(@x5y3_f,2.0,f(x,y)(pow(x,y)))", Square::f, true);
}

TEST(PowAsMapTest, cubed_dense_tensor_is_optimized) {
    verify_optimized("x5y3^3.0", Cube::f);
    verify_optimized("pow(x5y3,3.0)", Cube::f);
    verify_optimized("join(x5y3,3.0,f(x,y)(x^y))", Cube::f);
    verify_optimized("join(x5y3,3.0,f(x,y)(pow(x,y)))", Cube::f);
    verify_optimized("join(x5y3_f,3.0,f(x,y)(pow(x,y)))", Cube::f);
    verify_optimized("join(@x5y3,3.0,f(x,y)(pow(x,y)))", Cube::f, true);
    verify_optimized("join(@x5y3_f,3.0,f(x,y)(pow(x,y)))", Cube::f, true);
}

TEST(PowAsMapTest, hypercubed_dense_tensor_is_not_optimized) {
    verify_not_optimized("join(x5y3,4.0,f(x,y)(pow(x,y)))");
}

TEST(PowAsMapTest, scalar_join_is_optimized) {
    verify_optimized("join(a,2.0,f(x,y)(pow(x,y)))", Square::f);
}

TEST(PowAsMapTest, sparse_join_is_optimized) {
    verify_optimized("join(sparse,2.0,f(x,y)(pow(x,y)))", Square::f);
}

TEST(PowAsMapTest, mixed_join_is_optimized) {
    verify_optimized("join(mixed,2.0,f(x,y)(pow(x,y)))", Square::f);
}

GTEST_MAIN_RUN_ALL_TESTS()
