// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/test/time_bomb.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/input.h>
#include <vespa/vespalib/data/output.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/eval/eval/test/test_io.h>

using namespace vespalib;
using namespace vespalib::eval::test;
using vespalib::make_string_short::fmt;
using vespalib::slime::JsonFormat;
using vespalib::slime::Inspector;

std::string module_build_path("../../../../");
std::string binary = module_build_path + "src/apps/eval_expr/vespa-eval-expr";
std::string server_cmd = binary + " json-repl";

//-----------------------------------------------------------------------------

struct Result {
    std::string error;
    std::string result;
    std::vector<std::pair<std::string, std::string>> steps;

    Result(const Inspector &obj)
      : error(obj["error"].asString().make_string()),
        result(obj["result"].asString().make_string()),
        steps()
    {
        const auto &arr = obj["steps"];
        for (size_t i = 0; i < arr.entries(); ++i) {
            steps.emplace_back(arr[i]["class"].asString().make_string(),
                               arr[i]["symbol"].asString().make_string());
        }
    }
    void verify_result(const std::string &expect) {
        EXPECT_EQ(error, "");
        EXPECT_EQ(result, expect);
    }
    void verify_error(const std::string &expect) {
        EXPECT_EQ(steps.size(), 0u);
        EXPECT_EQ(result, "");
        fprintf(stderr, "... does error '%s' contain message '%s'?\n",
                error.c_str(), expect.c_str());
        EXPECT_TRUE(error.find(expect) != error.npos);
    }
    ~Result();
};
Result::~Result() = default;

struct Server : public ServerCmd {
    TimeBomb time_bomb;
    Server() : ServerCmd(server_cmd), time_bomb(60) {}
    Result eval(const std::string &expr, const std::string &name = {}, bool verbose = false) {
        Slime req;
        auto &obj = req.setObject();
        obj.setString("expr", expr.c_str());
        if (!name.empty()) {
            obj.setString("name", name.c_str());
        }
        if (verbose) {
            obj.setBool("verbose", true);
        }
        Slime reply = invoke(req);
        return {reply.get()};
    }
    ~Server() {
        EXPECT_EQ(shutdown(), 0);
    }
};

class EvalExprTest : public ::testing::Test {
protected:
    Server f1;
    EvalExprTest();
    ~EvalExprTest() override;
};

EvalExprTest::EvalExprTest()
    : ::testing::Test(),
      f1()
{
}

EvalExprTest::~EvalExprTest() = default;

//-----------------------------------------------------------------------------

TEST_F(EvalExprTest, print_server_command)
{
    fprintf(stderr, "server cmd: %s\n", server_cmd.c_str());
}

//-----------------------------------------------------------------------------

TEST_F(EvalExprTest, require_that_simple_evaluation_works)
{
    f1.eval("2+2").verify_result("4");
}

TEST_F(EvalExprTest, require_that_multiple_dependent_expressions_work)
{
    {
        SCOPED_TRACE("2+2");
        f1.eval("2+2", "a").verify_result("4");
    }
    {
        SCOPED_TRACE("a+2");
        f1.eval("a+2", "b").verify_result("6");
    }
    {
        SCOPED_TRACE("a+b");
        f1.eval("a+b").verify_result("10");
    }
}

TEST_F(EvalExprTest, require_that_symbols_can_be_overwritten)
{
    {
        SCOPED_TRACE("1");
        f1.eval("1", "a").verify_result("1");
    }
    {
        SCOPED_TRACE("1st 'a+1'");
        f1.eval("a+1", "a").verify_result("2");
    }
    {
        SCOPED_TRACE("2nd 'a+1'");
        f1.eval("a+1", "a").verify_result("3");
    }
    {
        SCOPED_TRACE("3rd 'a+1'");
        f1.eval("a+1", "a").verify_result("4");
    }
}

TEST_F(EvalExprTest, require_that_tensor_result_is_returned_in_verbose_verbatim_form)
{
    {
        SCOPED_TRACE("1");
        f1.eval("1", "a").verify_result("1");
    }
    {
        SCOPED_TRACE("2");
        f1.eval("2", "b").verify_result("2");
    }
    {
        SCOPED_TRACE("3");
        f1.eval("3", "c").verify_result("3");
    }
    {
        SCOPED_TRACE("tensor");
        f1.eval("tensor(x[3]):[a,b,c]").verify_result("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}");
    }
}

TEST_F(EvalExprTest, require_that_execution_steps_can_be_extracted)
{
    {
        SCOPED_TRACE("1");
        f1.eval("1", "a").verify_result("1");
    }
    {
        SCOPED_TRACE("2");
        f1.eval("2", "b").verify_result("2");
    }
    {
        SCOPED_TRACE("3");
        f1.eval("3", "c").verify_result("3");
    }
    auto res1 = f1.eval("a+b+c");
    auto res2 = f1.eval("a+b+c", "", true);
    EXPECT_EQ(res1.steps.size(), 0u);
    EXPECT_EQ(res2.steps.size(), 5u);
    for (const auto &step: res2.steps) {
        fprintf(stderr, "step:\n  class: %s\n    symbol: %s\n",
                step.first.c_str(), step.second.c_str());
    }
}

//-----------------------------------------------------------------------------

TEST_F(EvalExprTest, require_that_operation_batching_works)
{
    Slime req;
    auto &arr = req.setArray();
    auto &req1 = arr.addObject();
    req1.setString("expr", "2+2");
    req1.setString("name", "a");
    auto &req2 = arr.addObject();
    req2.setString("expr", "a+2");
    req2.setString("name", "b");
    auto &req3 = arr.addObject();
    req3.setString("expr", "this does not parse");
    auto &req4 = arr.addObject();
    req4.setString("expr", "a+b");
    Slime reply = f1.invoke(req);
    EXPECT_EQ(reply.get().entries(), 4u);
    EXPECT_TRUE(reply[2]["error"].asString().size > 0);
    EXPECT_EQ(reply[3]["result"].asString().make_string(), "10");
}

TEST_F(EvalExprTest, require_that_empty_operation_batch_works)
{
    Slime req;
    req.setArray();
    Slime reply = f1.invoke(req);
    EXPECT_TRUE(reply.get().type().getId() == slime::ARRAY::ID);
    EXPECT_EQ(reply.get().entries(), 0u);
}

//-----------------------------------------------------------------------------

TEST_F(EvalExprTest, require_that_empty_expression_produces_error)
{
    f1.eval("").verify_error("missing expression");
}

TEST_F(EvalExprTest, require_that_parse_error_produces_error)
{
    f1.eval("this does not parse").verify_error("expression parsing failed");
}

TEST_F(EvalExprTest, require_that_type_issues_produces_error)
{
    {
        SCOPED_TRACE("a");
        f1.eval("tensor(x[3]):[1,2,3]", "a").verify_result("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}");
    }
    {
        SCOPED_TRACE("b");
        f1.eval("tensor(x[2]):[4,5]", "b").verify_result("tensor(x[2]):{{x:0}:4,{x:1}:5}");
    }
    {
        SCOPED_TRACE("error");
        f1.eval("a+b").verify_error("type resolving failed");
    }
}

//-----------------------------------------------------------------------------

GTEST_MAIN_RUN_ALL_TESTS()
