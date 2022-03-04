// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/data/input.h>
#include <vespa/vespalib/data/output.h>
#include <vespa/vespalib/data/simple_buffer.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/eval/eval/test/test_io.h>

using namespace vespalib;
using namespace vespalib::eval::test;
using vespalib::make_string_short::fmt;
using vespalib::slime::JsonFormat;
using vespalib::slime::Inspector;

vespalib::string module_build_path("../../../../");
vespalib::string binary = module_build_path + "src/apps/eval_expr/vespa-eval-expr";
vespalib::string server_cmd = binary + " json-repl";

//-----------------------------------------------------------------------------

struct Result {
    vespalib::string error;
    vespalib::string result;
    std::vector<std::pair<vespalib::string, vespalib::string>> steps;

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
    void verify_result(const vespalib::string &expect) {
        EXPECT_EQUAL(error, "");
        EXPECT_EQUAL(result, expect);
    }
    void verify_error(const vespalib::string &expect) {
        EXPECT_EQUAL(steps.size(), 0u);
        EXPECT_EQUAL(result, "");
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
    Result eval(const vespalib::string &expr, const vespalib::string &name = {}, bool verbose = false) {
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
        EXPECT_EQUAL(shutdown(), 0);
    }
};

//-----------------------------------------------------------------------------

TEST("print server command") {
    fprintf(stderr, "server cmd: %s\n", server_cmd.c_str());
}

//-----------------------------------------------------------------------------

TEST_F("require that simple evaluation works", Server()) {
    TEST_DO(f1.eval("2+2").verify_result("4"));
}

TEST_F("require that multiple dependent expressions work", Server()) {
    TEST_DO(f1.eval("2+2", "a").verify_result("4"));
    TEST_DO(f1.eval("a+2", "b").verify_result("6"));
    TEST_DO(f1.eval("a+b").verify_result("10"));
}

TEST_F("require that symbols can be overwritten", Server()) {
    TEST_DO(f1.eval("1", "a").verify_result("1"));
    TEST_DO(f1.eval("a+1", "a").verify_result("2"));
    TEST_DO(f1.eval("a+1", "a").verify_result("3"));
    TEST_DO(f1.eval("a+1", "a").verify_result("4"));
}

TEST_F("require that tensor result is returned in verbose verbatim form", Server()) {
    TEST_DO(f1.eval("1", "a").verify_result("1"));
    TEST_DO(f1.eval("2", "b").verify_result("2"));
    TEST_DO(f1.eval("3", "c").verify_result("3"));
    TEST_DO(f1.eval("tensor(x[3]):[a,b,c]").verify_result("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}"));
}

TEST_F("require that execution steps can be extracted", Server()) {
    TEST_DO(f1.eval("1", "a").verify_result("1"));
    TEST_DO(f1.eval("2", "b").verify_result("2"));
    TEST_DO(f1.eval("3", "c").verify_result("3"));
    auto res1 = f1.eval("a+b+c");
    auto res2 = f1.eval("a+b+c", "", true);
    EXPECT_EQUAL(res1.steps.size(), 0u);
    EXPECT_EQUAL(res2.steps.size(), 5u);
    for (const auto &step: res2.steps) {
        fprintf(stderr, "step:\n  class: %s\n    symbol: %s\n",
                step.first.c_str(), step.second.c_str());
    }
}

//-----------------------------------------------------------------------------

TEST_F("require that operation batching works", Server()) {
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
    EXPECT_EQUAL(reply.get().entries(), 4u);
    EXPECT_TRUE(reply[2]["error"].asString().size > 0);
    EXPECT_EQUAL(reply[3]["result"].asString().make_string(), "10");
}

TEST_F("require that empty operation batch works", Server()) {
    Slime req;
    req.setArray();
    Slime reply = f1.invoke(req);
    EXPECT_TRUE(reply.get().type().getId() == slime::ARRAY::ID);
    EXPECT_EQUAL(reply.get().entries(), 0u);
}

//-----------------------------------------------------------------------------

TEST_F("require that empty expression produces error", Server()) {
    TEST_DO(f1.eval("").verify_error("missing expression"));
}

TEST_F("require that parse error produces error", Server()) {
    TEST_DO(f1.eval("this does not parse").verify_error("expression parsing failed"));
}

TEST_F("require that type issues produces error", Server()) {
    TEST_DO(f1.eval("tensor(x[3]):[1,2,3]", "a").verify_result("tensor(x[3]):{{x:0}:1,{x:1}:2,{x:2}:3}"));
    TEST_DO(f1.eval("tensor(x[2]):[4,5]", "b").verify_result("tensor(x[2]):{{x:0}:4,{x:1}:5}"));
    TEST_DO(f1.eval("a+b").verify_error("type resolving failed"));
}

//-----------------------------------------------------------------------------

TEST_MAIN() { TEST_RUN_ALL(); }
