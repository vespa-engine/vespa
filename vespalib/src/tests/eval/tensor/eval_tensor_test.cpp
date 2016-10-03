// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/eval/function.h>
#include <vespa/vespalib/tensor/tensor.h>
#include <vespa/vespalib/eval/interpreted_function.h>
#include <vespa/vespalib/tensor/default_tensor_engine.h>

using namespace vespalib;
using namespace vespalib::eval;

struct Check {
    const Value &value;
    explicit Check(const Value &value_in) : value(value_in) {}
    bool operator==(const Check &rhs) const { return value.equal(rhs.value); }  
};

std::ostream &operator<<(std::ostream &os, const Check &check) {
    const Value &value = check.value;
    if (value.is_error()) {
        os << "EVAL ERROR";
    } else if (value.is_double()) {
        os << value.as_double();
    } else if (value.is_tensor()) {
        os << value.as_tensor();
    }
    return os;
}

struct Eval {
    InterpretedFunction::Context ctx;
    InterpretedFunction ifun;
    const Value *result;
    explicit Eval(const vespalib::string &expr)
        : ctx(), ifun(tensor::DefaultTensorEngine::ref(), Function::parse(expr)), result(&ifun.eval(ctx)) {}
    bool operator==(const Eval &rhs) const { return result->equal(*rhs.result); }
};

std::ostream &operator<<(std::ostream &os, const Eval &eval) {
    os << Check(*eval.result);
    return os;
}

TEST_FF("require that eval errors are not equal", Eval("{"), Eval("{")) {
    EXPECT_TRUE(f1.result->is_error());
    EXPECT_TRUE(f2.result->is_error());
    EXPECT_NOT_EQUAL(f1, f2);
}

TEST("require that different tensors are not equal") {
    EXPECT_EQUAL(Eval("{{x:1}:1}"), Eval("{{x:1}:1}"));
    EXPECT_NOT_EQUAL(Eval("{{x:1}:1}"), Eval("{{x:1}:2}"));
    EXPECT_NOT_EQUAL(Eval("{{x:1}:1}"), Eval("{{x:2}:1}"));
    EXPECT_NOT_EQUAL(Eval("{{x:1}:1}"), Eval("{{y:1}:1}"));
    EXPECT_NOT_EQUAL(Eval("{{x:1}:1}"), Eval("{{x:1}:1,{x:2}:1}"));
}

TEST("require that tensor sum works") {
    EXPECT_EQUAL(Eval("6.0"), Eval("sum({{x:1}:1,{x:2}:2,{x:3}:3})"));
}

TEST("require that tensor sum over dimension works") {
    EXPECT_EQUAL(Eval("{{x:1}:4,{x:2}:6}"), Eval("sum({{x:1,y:1}:1,{x:2,y:1}:2,{x:1,y:2}:3,{x:2,y:2}:4},y)"));
    EXPECT_EQUAL(Eval("{{y:1}:3,{y:2}:7}"), Eval("sum({{x:1,y:1}:1,{x:2,y:1}:2,{x:1,y:2}:3,{x:2,y:2}:4},x)"));
}

TEST("require that tensor add works") {
    EXPECT_EQUAL(Eval("{{x:2}:5}"), Eval("{{x:1}:1,{x:2}:2} + {{x:2}:3,{x:3}:3}"));
    EXPECT_EQUAL(Eval("{{x:2}:5}"), Eval("{{x:2}:3,{x:3}:3} + {{x:1}:1,{x:2}:2}"));
}

TEST("require that tensor sub works") {
    EXPECT_EQUAL(Eval("{{x:2}:-1}"), Eval("{{x:1}:1,{x:2}:2} - {{x:2}:3,{x:3}:3}"));
    EXPECT_EQUAL(Eval("{{x:2}:1}"), Eval("{{x:2}:3,{x:3}:3} - {{x:1}:1,{x:2}:2}"));
}

TEST("require that tensor multiply works") {
    EXPECT_EQUAL(Eval("{{x:1,y:1}:3,{x:2,y:1}:6,{x:1,y:2}:4,{x:2,y:2}:8}"), Eval("{{x:1}:1,{x:2}:2}*{{y:1}:3,{y:2}:4}"));
}

TEST("require that tensor min works") {
    EXPECT_EQUAL(Eval("{{x:2}:2}"), Eval("min({{x:1}:1,{x:2}:2}, {{x:2}:3,{x:3}:3})"));
    EXPECT_EQUAL(Eval("{{x:2}:2}"), Eval("min({{x:2}:3,{x:3}:3}, {{x:1}:1,{x:2}:2})"));
}

TEST("require that tensor max works") {
    EXPECT_EQUAL(Eval("{{x:2}:3}"), Eval("max({{x:1}:1,{x:2}:2}, {{x:2}:3,{x:3}:3})"));
    EXPECT_EQUAL(Eval("{{x:2}:3}"), Eval("max({{x:2}:3,{x:3}:3}, {{x:1}:1,{x:2}:2})"));
}

TEST("require that tensor match works") {
    EXPECT_EQUAL(Eval("{{x:2}:6}"), Eval("match({{x:1}:1,{x:2}:2},{{x:2}:3,{x:3}:3})"));
}

TEST("require that tensor cell function works") {
    EXPECT_EQUAL(Eval("{{x:1}:3,{x:2}:4,{x:3}:5}"), Eval("{{x:1}:1,{x:2}:2,{x:3}:3}+2"));
    EXPECT_EQUAL(Eval("{{x:1}:3,{x:2}:4,{x:3}:5}"), Eval("2+{{x:1}:1,{x:2}:2,{x:3}:3}"));
    EXPECT_EQUAL(Eval("{{x:1}:-1,{x:2}:0,{x:3}:1}"), Eval("{{x:1}:1,{x:2}:2,{x:3}:3}-2"));
    EXPECT_EQUAL(Eval("{{x:1}:1,{x:2}:0,{x:3}:-1}"), Eval("2-{{x:1}:1,{x:2}:2,{x:3}:3}"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
