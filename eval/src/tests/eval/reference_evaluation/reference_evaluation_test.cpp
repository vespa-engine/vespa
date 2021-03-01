// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/test/reference_evaluation.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/test/eval_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;
using namespace vespalib::eval;
using namespace vespalib::eval::test;

//-----------------------------------------------------------------------------

TensorSpec ref_eval(const Function &fun, const std::vector<TensorSpec> &params) {
    return ReferenceEvaluation::eval(fun, params);
}

TensorSpec ref_eval(std::shared_ptr<const Function> fun, const std::vector<TensorSpec> &params) {
    return ref_eval(*fun, params);
}

TensorSpec ref_eval(const vespalib::string &expr, const std::vector<TensorSpec> &params) {
    return ref_eval(*Function::parse(expr), params);
}

TensorSpec make_val(const vespalib::string &expr) {
    return ref_eval(*Function::parse(expr), {});
}

//-----------------------------------------------------------------------------

struct MyEvalTest : EvalSpec::EvalTest {
    size_t pass_cnt = 0;
    size_t fail_cnt = 0;
    bool print_pass = false;
    bool print_fail = false;
    void next_expression(const std::vector<vespalib::string> &,
                         const vespalib::string &) override {}
    void handle_case(const std::vector<vespalib::string> &param_names,
                     const std::vector<double> &param_values,
                     const vespalib::string &expression,
                     double expected_result) override
    {
        auto function = Function::parse(param_names, expression);
        ASSERT_FALSE(function->has_error());
        std::vector<TensorSpec> params;
        for (double param: param_values) {
            params.push_back(TensorSpec("double").add({}, param));
        }
        auto eval_result = ref_eval(function, params);
        ASSERT_EQ(eval_result.type(), "double");
        double result = eval_result.as_double();
        if (is_same(expected_result, result)) {
            print_pass && fprintf(stderr, "verifying: %s -> %g ... PASS\n",
                                  as_string(param_names, param_values, expression).c_str(),
                                  expected_result);
            ++pass_cnt;
        } else {
            print_fail && fprintf(stderr, "verifying: %s -> %g ... FAIL: got %g\n",
                                  as_string(param_names, param_values, expression).c_str(),
                                  expected_result, result);
            ++fail_cnt;
        }
    }
};

//-----------------------------------------------------------------------------

TEST(ReferenceEvaluationTest, reference_evaluation_passes_all_eval_spec_tests) {
    MyEvalTest test;
    EvalSpec spec;
    test.print_fail = true;
    spec.add_all_cases();
    spec.each_case(test);
    EXPECT_GT(test.pass_cnt, 1000);
    EXPECT_EQ(test.fail_cnt, 0);
}

//-----------------------------------------------------------------------------

// 'make_val' will be used to generate tensor specs for inputs and
// expected outputs for other tests. In the production evaluation
// pipeline this kind of tensor create will be converted to a constant
// value when converting the Function to a TensorFunction. With the
// reference evaluation the Function is evaluated directly with no
// constant folding.

TEST(ReferenceEvaluationTest, constant_create_expression_works) {
    auto expect = TensorSpec("tensor(x{},y[2])")
                  .add({{"x", "a"}, {"y", 0}}, 1.0)
                  .add({{"x", "a"}, {"y", 1}}, 2.0);
    auto result = make_val("tensor(x{},y[2]):{a:[1,2]}");
    EXPECT_EQ(result, expect);
}

//-----------------------------------------------------------------------------

TEST(ReferenceEvaluationTest, parameter_expression_works) {
    auto a = make_val("tensor(x[2]):[1,2]");
    auto b = make_val("tensor(x[2]):[3,4]");
    auto fun_a = Function::parse({"a", "b"}, "a");
    auto fun_b = Function::parse({"a", "b"}, "b");
    EXPECT_EQ(ref_eval(fun_a, {a, b}), a);
    EXPECT_EQ(ref_eval(fun_b, {a, b}), b);
}

TEST(ReferenceEvaluationTest, parameter_expression_will_pad_with_zero) {
    auto a = TensorSpec("tensor(x[3])")
             .add({{"x", 1}}, 5.0);
    auto expect = make_val("tensor(x[3]):[0,5,0]");
    EXPECT_EQ(ref_eval("a", {a}), expect);
}

TEST(ReferenceEvaluationTest, reduce_expression_works) {
    auto a = make_val("tensor(x[2],y[2]):[[1,2],[3,4]]");
    auto expect = make_val("tensor(x[2]):[3,7]");
    EXPECT_EQ(ref_eval("reduce(a,sum,y)", {a}), expect);
}

TEST(ReferenceEvaluationTest, reduce_can_expand) {
    auto a = make_val("tensor(x{},y[2]):{}");
    auto expect = make_val("tensor(y[2]):[0,0]");
    EXPECT_EQ(ref_eval("reduce(a,sum,x)", {a}), expect);
}

TEST(ReferenceEvaluationTest, map_expression_works) {
    auto a = make_val("tensor(x[2]):[1,10]");
    auto expect = make_val("tensor(x[2]):[5,23]");
    EXPECT_EQ(ref_eval("map(a,f(x)(x*2+3))", {a}), expect);
}

TEST(ReferenceEvaluationTest, join_expression_works) {
    auto a = make_val("tensor(x[2]):[1,2]");
    auto b = make_val("tensor(y[2]):[3,4]");
    auto expect = make_val("tensor(x[2],y[2]):[[4,5],[5,6]]");
    EXPECT_EQ(ref_eval("join(a,b,f(x,y)(x+y))", {a, b}), expect);
}

TEST(ReferenceEvaluationTest, merge_expression_works) {
    auto a = make_val("tensor(x{}):{a:1,b:2,c:3}");
    auto b = make_val("tensor(x{}):{c:3,d:4}");
    auto expect = make_val("tensor(x{}):{a:1,b:2,c:6,d:4}");
    EXPECT_EQ(ref_eval("merge(a,b,f(x,y)(x+y))", {a, b}), expect);
}

TEST(ReferenceEvaluationTest, concat_expression_works) {
    auto a = make_val("tensor(x[2]):[1,2]");
    auto b = make_val("tensor(x[2]):[3,4]");
    auto expect = make_val("tensor(x[4]):[1,2,3,4]");
    EXPECT_EQ(ref_eval("concat(a,b,x)", {a, b}), expect);
}

TEST(ReferenceEvaluationTest, cell_cast_expression_works) {
    auto a = make_val("tensor<double>(x[4]):[1,2,3,4]");
    auto expect = make_val("tensor<float>(x[4]):[1,2,3,4]");
    EXPECT_EQ(ref_eval("cell_cast(a,float)", {a}), expect);
}

TEST(ReferenceEvaluationTest, rename_expression_works) {
    auto a = make_val("tensor(x[2]):[1,2]");
    auto expect = make_val("tensor(y[2]):[1,2]");
    EXPECT_EQ(ref_eval("rename(a,x,y)", {a}), expect);
}

TEST(ReferenceEvaluationTest, create_expression_works) {
    auto a = make_val("5");
    auto expect = make_val("tensor(x[3]):[5,10,15]");
    EXPECT_EQ(ref_eval("tensor(x[3]):[a,2*a,3*a]", {a}), expect);
}

TEST(ReferenceEvaluationTest, tensor_create_will_pad_with_zero) {
    auto a = make_val("5");
    auto expect = make_val("tensor(x[3]):[0,5,0]");
    EXPECT_EQ(ref_eval("tensor(x[3]):{{x:1}:a}", {a}), expect);
}

TEST(ReferenceEvaluationTest, lambda_expression_works) {
    auto a = make_val("5");
    auto expect = make_val("tensor(x[3]):[5,10,15]");
    EXPECT_EQ(ref_eval("tensor(x[3])((x+1)*a)", {a}), expect);
}

TEST(ReferenceEvaluationTest, peek_expression_works) {
    auto a = make_val("tensor(x{},y[2]):{a:[3,7]}");
    auto b = make_val("1");
    auto expect = make_val("7");
    EXPECT_EQ(ref_eval("a{x:a,y:(b)}", {a, b}), expect);
}

TEST(ReferenceEvaluationTest, verbatim_peek_of_dense_dimension_works) {
    auto a = make_val("tensor(x[4]):[1,2,3,4]");
    auto expect = make_val("3");
    EXPECT_EQ(ref_eval("a{x:2}", {a}), expect);
}

TEST(ReferenceEvaluationTest, out_of_bounds_peek_works) {
    auto a = make_val("tensor(x[4]):[1,2,3,4]");
    auto b = make_val("4");
    auto expect = make_val("0");
    EXPECT_EQ(ref_eval("a{x:(b)}", {a, b}), expect);
}

//-----------------------------------------------------------------------------

TEST(ReferenceEvaluationTest, compound_expression_works) {
    auto a = make_val("10");
    auto b = make_val("20");
    auto expect = make_val("20");
    EXPECT_EQ(ref_eval("reduce(concat(a,b,x)+5,avg,x)", {a, b}), expect);
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
