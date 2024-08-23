// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/eval/eval/function.h>
#include <vespa/eval/eval/llvm/compiled_function.h>
#include <vespa/eval/eval/test/eval_spec.h>
#include <vespa/eval/eval/basic_nodes.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cmath>
#include <iostream>

using namespace vespalib::eval;
using vespalib::test::Nexus;

//-----------------------------------------------------------------------------

std::vector<std::string> params_10({"p1", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9", "p10"});

const char *expr_10 = "p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10";

TEST(CompiledFunctionTest, require_that_separate_parameter_passing_works)
{
    CompiledFunction cf_10(*Function::parse(params_10, expr_10), PassParams::SEPARATE);
    auto fun_10 = cf_10.get_function<10>();
    EXPECT_EQ(10.0, fun_10(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0));
    EXPECT_EQ(50.0, fun_10(5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0));
    EXPECT_EQ(45.0, fun_10(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0));
    EXPECT_EQ(45.0, fun_10(9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0, 0.0));
}

TEST(CompiledFunctionTest, require_that_array_parameter_passing_works)
{
    CompiledFunction arr_cf(*Function::parse(params_10, expr_10), PassParams::ARRAY);
    auto arr_fun = arr_cf.get_function();
    auto eval_arr_fun = [&arr_fun](std::vector<double> args) { return arr_fun(&args[0]); };
    EXPECT_EQ(10.0, eval_arr_fun({1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0}));
    EXPECT_EQ(50.0, eval_arr_fun({5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0}));
    EXPECT_EQ(45.0, eval_arr_fun({0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0}));
    EXPECT_EQ(45.0, eval_arr_fun({9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0, 0.0}));
}

double my_resolve(void *ctx, size_t idx) { return ((double *)ctx)[idx]; }

TEST(CompiledFunctionTest, require_that_lazy_parameter_passing_works)
{
    CompiledFunction lazy_cf(*Function::parse(params_10, expr_10), PassParams::LAZY);
    auto lazy_fun = lazy_cf.get_lazy_function();
    auto eval_lazy_fun = [&lazy_fun](std::vector<double> args) { return lazy_fun(my_resolve, &args[0]); };
    EXPECT_EQ(10.0, eval_lazy_fun({1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0}));
    EXPECT_EQ(50.0, eval_lazy_fun({5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0}));
    EXPECT_EQ(45.0, eval_lazy_fun({0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0}));
    EXPECT_EQ(45.0, eval_lazy_fun({9.0, 8.0, 7.0, 6.0, 5.0, 4.0, 3.0, 2.0, 1.0, 0.0}));
}

//-----------------------------------------------------------------------------

std::vector<std::string> unsupported = {
    "map(",
    "map_subspaces(",
    "join(",
    "merge(",
    "reduce(",
    "rename(",
    "tensor(",
    "concat(",
    "cell_cast("
};

bool is_unsupported(const std::string &expression) {
    if (expression.find("{") != std::string::npos) {
        return true;
    }
    for (const auto &prefix: unsupported) {
        if (expression.starts_with(prefix)) {
            return true;
        }
    }
    return false;
}

//-----------------------------------------------------------------------------

struct MyEvalTest : test::EvalSpec::EvalTest {
    size_t pass_cnt = 0;
    size_t fail_cnt = 0;
    bool print_pass = false;
    bool print_fail = false;

    ~MyEvalTest() override;
    virtual void next_expression(const std::vector<std::string> &param_names,
                                 const std::string &expression) override
    {
        auto function = Function::parse(param_names, expression);
        ASSERT_TRUE(!function->has_error());
        bool is_supported = !is_unsupported(expression);
        bool has_issues = CompiledFunction::detect_issues(*function);
        if (is_supported == has_issues) {
            const char *supported_str = is_supported ? "supported" : "not supported";
            const char *issues_str = has_issues ? "has issues" : "does not have issues";
            print_fail && fprintf(stderr, "expression %s is %s, but %s\n",
                                  expression.c_str(), supported_str, issues_str);
            ++fail_cnt;
        }
    }
    virtual void handle_case(const std::vector<std::string> &param_names,
                             const std::vector<double> &param_values,
                             const std::string &expression,
                             double expected_result) override
    {
        auto function = Function::parse(param_names, expression);
        ASSERT_TRUE(!function->has_error());
        bool is_supported = !is_unsupported(expression);
        bool has_issues = CompiledFunction::detect_issues(*function);
        if (is_supported && !has_issues) {
            CompiledFunction cfun(*function, PassParams::ARRAY);
            auto fun = cfun.get_function();
            ASSERT_EQ(cfun.num_params(), param_values.size());
            double result = fun(param_values.data());
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
    }
};

MyEvalTest::~MyEvalTest() = default;

TEST(CompiledFunctionTest, require_that_compiled_evaluation_passes_all_conformance_tests)
{
    MyEvalTest f1;
    test::EvalSpec f2;
    f1.print_fail = true;
    f2.add_all_cases();
    f2.each_case(f1);
    EXPECT_GT(f1.pass_cnt, 1000u);
    EXPECT_EQ(0u, f1.fail_cnt);
}

//-----------------------------------------------------------------------------

TEST(CompiledFunctionTest, require_that_large_plugin_based_set_membership_checks_work)
{
    auto my_in = std::make_unique<nodes::In>(std::make_unique<nodes::Symbol>(0));
    for(size_t i = 1; i <= 100; ++i) {
        my_in->add_entry(std::make_unique<nodes::Number>(i));
    }
    auto my_fun = Function::create(std::move(my_in), {"a"});
    CompiledFunction cf(*my_fun, PassParams::SEPARATE);
    CompiledFunction arr_cf(*my_fun, PassParams::ARRAY);
    auto fun = cf.get_function<1>();
    auto arr_fun = arr_cf.get_function();
    for (double value = 0.5; value <= 100.5; value += 0.5) {
        if (std::round(value) == value) {
            EXPECT_EQ(1.0, fun(value));
            EXPECT_EQ(1.0, arr_fun(&value));
        } else {
            EXPECT_EQ(0.0, fun(value));
            EXPECT_EQ(0.0, arr_fun(&value));
        }
    }
}

//-----------------------------------------------------------------------------

CompiledFunction pass_fun(CompiledFunction cf) {
    auto fun = cf.get_function<2>();
    EXPECT_EQ(5.0, fun(2.0, 3.0));
    return cf;
}

TEST(CompiledFunctionTest, require_that_compiled_expression_can_be_moved_around)
{
    CompiledFunction cf(*Function::parse("a+b"), PassParams::SEPARATE);
    auto fun = cf.get_function<2>();
    EXPECT_EQ(4.0, fun(2.0, 2.0));
    CompiledFunction cf2 = pass_fun(std::move(cf));
    EXPECT_TRUE(cf.get_function<2>() == nullptr);
    auto fun2 = cf2.get_function<2>();
    EXPECT_TRUE(fun == fun2);
    EXPECT_EQ(10.0, fun(3.0, 7.0));
}

TEST(CompiledFunctionTest, require_that_expressions_with_constant_sub_expressions_evaluate_correctly)
{
    CompiledFunction cf(*Function::parse("if(1,2,10)+a+b+max(1,2)/1"), PassParams::SEPARATE);
    auto fun = cf.get_function<2>();
    EXPECT_EQ(7.0, fun(1.0, 2.0));
    EXPECT_EQ(11.0, fun(3.0, 4.0));
}

TEST(CompiledFunctionTest, dump_ir_code_to_verify_lazy_casting)
{
    auto function = Function::parse({"a", "b"}, "12==2+if(a==3&&a<10||b,10,5)");
    LLVMWrapper wrapper;
    size_t id = wrapper.make_function(function->num_params(), PassParams::SEPARATE, function->root(), {});
    wrapper.compile(llvm::dbgs()); // dump module before compiling it
    using fun_type = double (*)(double, double);
    fun_type fun = (fun_type) wrapper.get_function_address(id);
    EXPECT_EQ(0.0, fun(0.0, 0.0));
    EXPECT_EQ(1.0, fun(0.0, 1.0));
    EXPECT_EQ(1.0, fun(3.0, 0.0));
}

namespace {

void verify_that_multithreaded_compilation_works()
{
    for (size_t i = 0; i < 16; ++i) {
        {
            CompiledFunction cf(*Function::parse({"x", "y", "z", "w"}, "((x+1)*(y-1))/((z+1)/(w-1))"),
                                PassParams::SEPARATE);
            auto fun = cf.get_function<4>();
            EXPECT_EQ(1.0, fun(0.0, 2.0, 0.0, 2.0));
        }
        {
            CompiledFunction cf(*Function::parse({"x", "y", "z", "w"}, "((x+1)*(y-1))/((z+1)/(w-1))"),
                                PassParams::SEPARATE);
            auto fun = cf.get_function<4>();
            EXPECT_EQ(4.0, fun(1.0, 3.0, 0.0, 2.0));
        }
        {
            CompiledFunction cf(*Function::parse({"x", "y", "z", "w"}, "((x+1)*(y-1))/((z+1)/(w-1))"),
                                PassParams::SEPARATE);
            auto fun = cf.get_function<4>();
            EXPECT_EQ(2.0, fun(1.0, 3.0, 1.0, 2.0));
        }
        {
            CompiledFunction cf(*Function::parse({"x", "y", "z", "w"}, "((x+1)*(y-1))/((z+1)/(w-1))"),
                                PassParams::SEPARATE);
            auto fun = cf.get_function<4>();
            EXPECT_EQ(8.0, fun(1.0, 3.0, 1.0, 5.0));
        }
    }
}

}

TEST(CompiledFunctionTest, require_that_multithreaded_compilation_works)
{
    constexpr size_t num_threads = 32;
    auto task = [](Nexus&) { verify_that_multithreaded_compilation_works(); };
    Nexus::run(num_threads, task);
}

//-----------------------------------------------------------------------------

TEST(CompiledFunctionTest, require_that_function_issues_can_be_detected)
{
    auto simple = Function::parse("a+b");
    auto complex = Function::parse("join(a,b,f(a,b)(a+b))");
    EXPECT_FALSE(simple->has_error());
    EXPECT_FALSE(complex->has_error());
    EXPECT_FALSE(CompiledFunction::detect_issues(*simple));
    EXPECT_TRUE(CompiledFunction::detect_issues(*complex));
    std::cerr << "Example function issues:" << std::endl
              << testing::PrintToString(CompiledFunction::detect_issues(*complex).list)
              << std::endl;
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
