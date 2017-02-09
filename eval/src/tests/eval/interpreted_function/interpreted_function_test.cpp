// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/tensor_spec.h>
#include <vespa/eval/eval/interpreted_function.h>
#include <vespa/eval/eval/test/eval_spec.h>
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <iostream>

using namespace vespalib::eval;
using vespalib::Stash;

//-----------------------------------------------------------------------------

struct MyEvalTest : test::EvalSpec::EvalTest {
    size_t pass_cnt = 0;
    size_t fail_cnt = 0;
    bool print_pass = false;
    bool print_fail = false;
    virtual void next_expression(const std::vector<vespalib::string> &param_names,
                                 const vespalib::string &expression) override
    {
        Function function = Function::parse(param_names, expression);
        ASSERT_TRUE(!function.has_error());
        bool is_supported = true;
        bool has_issues = InterpretedFunction::detect_issues(function);
        if (is_supported == has_issues) {
            const char *supported_str = is_supported ? "supported" : "not supported";
            const char *issues_str = has_issues ? "has issues" : "does not have issues";
            print_fail && fprintf(stderr, "expression %s is %s, but %s\n",
                                  expression.c_str(), supported_str, issues_str);
            ++fail_cnt;
        }
    }
    virtual void handle_case(const std::vector<vespalib::string> &param_names,
                             const std::vector<double> &param_values,
                             const vespalib::string &expression,
                             double expected_result) override
    {
        Function function = Function::parse(param_names, expression);
        ASSERT_TRUE(!function.has_error());
        bool is_supported = true;
        bool has_issues = InterpretedFunction::detect_issues(function);
        if (is_supported && !has_issues) {
            InterpretedFunction ifun(SimpleTensorEngine::ref(), function, NodeTypes());
            ASSERT_EQUAL(ifun.num_params(), param_values.size());
            InterpretedFunction::Context ictx(ifun);
            for (double param: param_values) {
                ictx.add_param(param);
            }
            const Value &result_value = ifun.eval(ictx);
            double result = result_value.as_double();
            if (result_value.is_double() && is_same(expected_result, result)) {
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
    }
};

TEST_FF("require that compiled evaluation passes all conformance tests", MyEvalTest(), test::EvalSpec()) {
    f1.print_fail = true;
    f2.add_all_cases();
    f2.each_case(f1);
    EXPECT_GREATER(f1.pass_cnt, 1000u);
    EXPECT_EQUAL(0u, f1.fail_cnt);
}

//-----------------------------------------------------------------------------

TEST("require that invalid function evaluates to a error") {
    std::vector<vespalib::string> params({"x", "y", "z", "w"});
    Function function = Function::parse(params, "x & y");
    EXPECT_TRUE(function.has_error());
    InterpretedFunction ifun(SimpleTensorEngine::ref(), function, NodeTypes());
    InterpretedFunction::Context ctx(ifun);
    ctx.add_param(1);
    ctx.add_param(2);
    ctx.add_param(3);
    ctx.add_param(4);
    const Value &result = ifun.eval(ctx);
    EXPECT_TRUE(result.is_error());
    EXPECT_EQUAL(error_value, result.as_double());
}

//-----------------------------------------------------------------------------

size_t count_ifs(const vespalib::string &expr, std::initializer_list<double> params_in) {
    Function fun = Function::parse(expr);
    InterpretedFunction ifun(SimpleTensorEngine::ref(), fun, NodeTypes());
    InterpretedFunction::Context ctx(ifun);
    for (double param: params_in) {
        ctx.add_param(param);
    }
    ifun.eval(ctx);
    return ctx.if_cnt();
}

TEST("require that if_cnt in eval context is updated correctly") {
    EXPECT_EQUAL(0u, count_ifs("1", {}));
    EXPECT_EQUAL(1u, count_ifs("if(a<10,if(a<9,if(a<8,if(a<7,5,4),3),2),1)", {10}));
    EXPECT_EQUAL(2u, count_ifs("if(a<10,if(a<9,if(a<8,if(a<7,5,4),3),2),1)", {9}));
    EXPECT_EQUAL(3u, count_ifs("if(a<10,if(a<9,if(a<8,if(a<7,5,4),3),2),1)", {8}));
    EXPECT_EQUAL(4u, count_ifs("if(a<10,if(a<9,if(a<8,if(a<7,5,4),3),2),1)", {7}));
    EXPECT_EQUAL(4u, count_ifs("if(a<10,if(a<9,if(a<8,if(a<7,5,4),3),2),1)", {6}));
}

//-----------------------------------------------------------------------------

TEST("require that interpreted function instructions have expected size") {
    EXPECT_EQUAL(sizeof(InterpretedFunction::Instruction), 16u);
}

TEST("require that basic addition works") {
    Function function = Function::parse("a+10");
    InterpretedFunction interpreted(SimpleTensorEngine::ref(), function, NodeTypes());
    InterpretedFunction::Context ctx(interpreted);
    ctx.add_param(20);
    EXPECT_EQUAL(interpreted.eval(ctx).as_double(), 30.0);
    ctx.clear_params();
    ctx.add_param(40);
    EXPECT_EQUAL(interpreted.eval(ctx).as_double(), 50.0);
}

//-----------------------------------------------------------------------------

TEST("require that dot product like expression is not optimized for unknown types") {
    const TensorEngine &engine = SimpleTensorEngine::ref();
    Function function = Function::parse("sum(a*b)");
    DoubleValue a(2.0);
    DoubleValue b(3.0);
    double expect = (2.0 * 3.0);
    InterpretedFunction interpreted(engine, function, NodeTypes());
    EXPECT_EQUAL(4u, interpreted.program_size());
    InterpretedFunction::Context ctx(interpreted);
    ctx.add_param(a);
    ctx.add_param(b);
    const Value &result = interpreted.eval(ctx);
    EXPECT_TRUE(result.is_double());
    EXPECT_EQUAL(expect, result.as_double());
}

TEST("require that dot product works with tensor function") {
    const TensorEngine &engine = SimpleTensorEngine::ref();
    Function function = Function::parse("sum(a*b)");
    auto a = TensorSpec("tensor(x[3])")
             .add({{"x", 0}}, 5.0)
             .add({{"x", 1}}, 3.0)
             .add({{"x", 2}}, 2.0);
    auto b = TensorSpec("tensor(x[3])")
             .add({{"x", 0}}, 7.0)
             .add({{"x", 1}}, 11.0)
             .add({{"x", 2}}, 13.0);
    double expect = ((5.0 * 7.0) + (3.0 * 11.0) + (2.0 * 13.0));
    NodeTypes types(function, {ValueType::from_spec(a.type()), ValueType::from_spec(a.type())});
    InterpretedFunction interpreted(engine, function, types);
    EXPECT_EQUAL(1u, interpreted.program_size());
    InterpretedFunction::Context ctx(interpreted);
    TensorValue va(engine.create(a));
    TensorValue vb(engine.create(b));
    ctx.add_param(va);
    ctx.add_param(vb);
    const Value &result = interpreted.eval(ctx);
    EXPECT_TRUE(result.is_double());
    EXPECT_EQUAL(expect, result.as_double());
}

TEST("require that matrix multiplication works with tensor function") {
    const TensorEngine &engine = SimpleTensorEngine::ref();
    Function function = Function::parse("sum(a*b,y)");
    auto a = TensorSpec("tensor(x[2],y[2])")
             .add({{"x", 0},{"y", 0}},  1.0)
             .add({{"x", 0},{"y", 1}},  2.0)
             .add({{"x", 1},{"y", 0}},  3.0)
             .add({{"x", 1},{"y", 1}},  5.0);
    auto b = TensorSpec("tensor(y[2],z[2])")
             .add({{"y", 0},{"z", 0}},  7.0)
             .add({{"y", 0},{"z", 1}}, 11.0)
             .add({{"y", 1},{"z", 0}}, 13.0)
             .add({{"y", 1},{"z", 1}}, 17.0);
    auto expect = TensorSpec("tensor(x[2],z[2])")
                  .add({{"x", 0},{"z", 0}}, (1.0 *  7.0) + (2.0 * 13.0))
                  .add({{"x", 0},{"z", 1}}, (1.0 * 11.0) + (2.0 * 17.0))
                  .add({{"x", 1},{"z", 0}}, (3.0 *  7.0) + (5.0 * 13.0))
                  .add({{"x", 1},{"z", 1}}, (3.0 * 11.0) + (5.0 * 17.0));
    NodeTypes types(function, {ValueType::from_spec(a.type()), ValueType::from_spec(a.type())});
    InterpretedFunction interpreted(engine, function, types);
    EXPECT_EQUAL(1u, interpreted.program_size());
    InterpretedFunction::Context ctx(interpreted);
    TensorValue va(engine.create(a));
    TensorValue vb(engine.create(b));
    ctx.add_param(va);
    ctx.add_param(vb);
    const Value &result = interpreted.eval(ctx);
    ASSERT_TRUE(result.is_tensor());
    EXPECT_EQUAL(expect, engine.to_spec(*result.as_tensor()));
}

//-----------------------------------------------------------------------------

TEST("require that functions with non-compilable lambdas cannot be interpreted") {
    auto good_map = Function::parse("map(a,f(x)(x+1))");
    auto good_join = Function::parse("join(a,b,f(x,y)(x+y))");
    auto good_tensor = Function::parse("tensor(a[10],b[10])(a+b)");
    auto bad_map = Function::parse("map(a,f(x)(map(x,f(i)(i+1))))");
    auto bad_join = Function::parse("join(a,b,f(x,y)(join(x,y,f(i,j)(i+j))))");
    auto bad_tensor = Function::parse("tensor(a[10],b[10])(join(a,b,f(i,j)(i+j)))");
    for (const Function *good: {&good_map, &good_join, &good_tensor}) {
        if (!EXPECT_TRUE(!good->has_error())) {
            fprintf(stderr, "parse error: %s\n", good->get_error().c_str());
        }
        EXPECT_TRUE(!InterpretedFunction::detect_issues(*good));
    }
    for (const Function *bad: {&bad_map, &bad_join, &bad_tensor}) {
        if (!EXPECT_TRUE(!bad->has_error())) {
            fprintf(stderr, "parse error: %s\n", bad->get_error().c_str());
        }
        EXPECT_TRUE(InterpretedFunction::detect_issues(*bad));
    }
    std::cerr << "Example function issues:" << std::endl
              << InterpretedFunction::detect_issues(bad_tensor).list
              << std::endl;
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
