// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/eval/function.h>
#include <vespa/vespalib/eval/interpreted_function.h>
#include <vespa/vespalib/eval/eval_spec.h>
#include <vespa/vespalib/eval/basic_nodes.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/stash.h>

using namespace vespalib::eval;
using vespalib::Stash;

//-----------------------------------------------------------------------------

struct MyEvalTest : test::EvalSpec::EvalTest {
    size_t pass_cnt = 0;
    size_t fail_cnt = 0;
    bool print_pass = false;
    bool print_fail = false;
    virtual void next_expression(const std::vector<vespalib::string> &,
                                 const vespalib::string &) override {}
    virtual void handle_case(const std::vector<vespalib::string> &param_names,
                             const std::vector<double> &param_values,
                             const vespalib::string &expression,
                             double expected_result) override
    {
        Function fun = Function::parse(param_names, expression);
        EXPECT_EQUAL(fun.num_params(), param_values.size());
        InterpretedFunction ifun(SimpleTensorEngine::ref(), fun);
        InterpretedFunction::Context ictx;
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
    InterpretedFunction ifun(SimpleTensorEngine::ref(), function);
    InterpretedFunction::Context ctx;
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
    InterpretedFunction ifun(SimpleTensorEngine::ref(), fun);
    InterpretedFunction::Context ctx;
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
    InterpretedFunction interpreted(SimpleTensorEngine::ref(), function);
    InterpretedFunction::Context ctx;
    ctx.add_param(20);
    EXPECT_EQUAL(interpreted.eval(ctx).as_double(), 30.0);
    ctx.clear_params();
    ctx.add_param(40);
    EXPECT_EQUAL(interpreted.eval(ctx).as_double(), 50.0);
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
