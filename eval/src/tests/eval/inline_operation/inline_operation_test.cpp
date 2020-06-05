// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/eval/eval/function.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib::eval;
using namespace vespalib::eval::operation;

template <typename T> struct IsInlined { constexpr static bool value = true; };
template <> struct IsInlined<CallOp1> { constexpr static bool value = false; };
template <> struct IsInlined<CallOp2> { constexpr static bool value = false; };

template <typename T> double test_op1(op1_t ref, double a, bool inlined) {
    T op(ref);
    EXPECT_EQ(IsInlined<T>::value, inlined);
    EXPECT_EQ(op(a), ref(a));
    return op(a);
};

template <typename T> double test_op2(op2_t ref, double a, double b, bool inlined) {
    T op(ref);
    EXPECT_EQ(IsInlined<T>::value, inlined);
    EXPECT_EQ(op(a,b), ref(a,b));
    return op(a,b);
};

op1_t as_op1(const vespalib::string &str) {
    auto fun = Function::parse({"a"}, str);
    auto res = lookup_op1(*fun);
    EXPECT_TRUE(res.has_value());
    return res.value();
}

op2_t as_op2(const vespalib::string &str) {
    auto fun = Function::parse({"a", "b"}, str);
    auto res = lookup_op2(*fun);
    EXPECT_TRUE(res.has_value());
    return res.value();
}

TEST(InlineOperationTest, op1_lambdas_are_recognized) {
    EXPECT_EQ(as_op1("-a"),         Neg::f);
    EXPECT_EQ(as_op1("!a"),         Not::f);
    EXPECT_EQ(as_op1("cos(a)"),     Cos::f);
    EXPECT_EQ(as_op1("sin(a)"),     Sin::f);
    EXPECT_EQ(as_op1("tan(a)"),     Tan::f);
    EXPECT_EQ(as_op1("cosh(a)"),    Cosh::f);
    EXPECT_EQ(as_op1("sinh(a)"),    Sinh::f);
    EXPECT_EQ(as_op1("tanh(a)"),    Tanh::f);
    EXPECT_EQ(as_op1("acos(a)"),    Acos::f);
    EXPECT_EQ(as_op1("asin(a)"),    Asin::f);
    EXPECT_EQ(as_op1("atan(a)"),    Atan::f);
    EXPECT_EQ(as_op1("exp(a)"),     Exp::f);
    EXPECT_EQ(as_op1("log10(a)"),   Log10::f);
    EXPECT_EQ(as_op1("log(a)"),     Log::f);
    EXPECT_EQ(as_op1("sqrt(a)"),    Sqrt::f);
    EXPECT_EQ(as_op1("ceil(a)"),    Ceil::f);
    EXPECT_EQ(as_op1("fabs(a)"),    Fabs::f);
    EXPECT_EQ(as_op1("floor(a)"),   Floor::f);
    EXPECT_EQ(as_op1("isNan(a)"),   IsNan::f);
    EXPECT_EQ(as_op1("relu(a)"),    Relu::f);
    EXPECT_EQ(as_op1("sigmoid(a)"), Sigmoid::f);
    EXPECT_EQ(as_op1("elu(a)"),     Elu::f);
}

TEST(InlineOperationTest, op1_lambdas_are_recognized_with_different_parameter_names) {
    EXPECT_EQ(lookup_op1(*Function::parse({"x"}, "-x")).value(), Neg::f);
    EXPECT_EQ(lookup_op1(*Function::parse({"x"}, "!x")).value(), Not::f);
}

TEST(InlineOperationTest, non_op1_lambdas_are_not_recognized) {
    EXPECT_FALSE(lookup_op1(*Function::parse({"a"}, "a*a")).has_value());
    EXPECT_FALSE(lookup_op1(*Function::parse({"a", "b"}, "a+b")).has_value());
}

TEST(InlineOperationTest, op2_lambdas_are_recognized) {
    EXPECT_EQ(as_op2("a+b"),        Add::f);
    EXPECT_EQ(as_op2("a-b"),        Sub::f);
    EXPECT_EQ(as_op2("a*b"),        Mul::f);
    EXPECT_EQ(as_op2("a/b"),        Div::f);
    EXPECT_EQ(as_op2("a%b"),        Mod::f);
    EXPECT_EQ(as_op2("a^b"),        Pow::f);
    EXPECT_EQ(as_op2("a==b"),       Equal::f);
    EXPECT_EQ(as_op2("a!=b"),       NotEqual::f);
    EXPECT_EQ(as_op2("a~=b"),       Approx::f);
    EXPECT_EQ(as_op2("a<b"),        Less::f);
    EXPECT_EQ(as_op2("a<=b"),       LessEqual::f);
    EXPECT_EQ(as_op2("a>b"),        Greater::f);
    EXPECT_EQ(as_op2("a>=b"),       GreaterEqual::f);
    EXPECT_EQ(as_op2("a&&b"),       And::f);
    EXPECT_EQ(as_op2("a||b"),       Or::f);
    EXPECT_EQ(as_op2("atan2(a,b)"), Atan2::f);
    EXPECT_EQ(as_op2("ldexp(a,b)"), Ldexp::f);
    EXPECT_EQ(as_op2("pow(a,b)"),   Pow::f);
    EXPECT_EQ(as_op2("fmod(a,b)"),  Mod::f);
    EXPECT_EQ(as_op2("min(a,b)"),   Min::f);
    EXPECT_EQ(as_op2("max(a,b)"),   Max::f);
}

TEST(InlineOperationTest, op2_lambdas_are_recognized_with_different_parameter_names) {
    EXPECT_EQ(lookup_op2(*Function::parse({"x", "y"}, "x+y")).value(), Add::f);
    EXPECT_EQ(lookup_op2(*Function::parse({"x", "y"}, "x-y")).value(), Sub::f);
}

TEST(InlineOperationTest, non_op2_lambdas_are_not_recognized) {
    EXPECT_FALSE(lookup_op2(*Function::parse({"a"}, "-a")).has_value());
    EXPECT_FALSE(lookup_op2(*Function::parse({"a", "b"}, "b+a")).has_value());
}

TEST(InlineOperationTest, generic_op1_wrapper_works) {
    CallOp1 op(Neg::f);
    EXPECT_EQ(op(3), -3);
    EXPECT_EQ(op(-5), 5);
}

TEST(InlineOperationTest, generic_op2_wrapper_works) {
    CallOp2 op(Add::f);
    EXPECT_EQ(op(2,3), 5);
    EXPECT_EQ(op(3,7), 10);
}

TEST(InlineOperationTest, inline_op2_example_works) {
    op2_t ignored = nullptr;
    InlineOp2<Add> op(ignored);
    EXPECT_EQ(op(2,3), 5);
    EXPECT_EQ(op(3,7), 10);
}

TEST(InlineOperationTest, parameter_swap_wrapper_works) {
    CallOp2 op(Sub::f);
    SwapArgs2<CallOp2> swap_op(Sub::f);
    EXPECT_EQ(op(2,3), -1);
    EXPECT_EQ(swap_op(2,3), 1);
    EXPECT_EQ(op(3,7), -4);
    EXPECT_EQ(swap_op(3,7), 4);
}

TEST(InlineOperationTest, resolved_op1_works) {
    auto a = TypifyOp1::resolve(Neg::f, [](auto t){ return test_op1<typename decltype(t)::type>(Neg::f, 2.0, false); });
    // putting the lambda inside the EXPECT does not work
    EXPECT_EQ(a, -2.0);
}

TEST(InlineOperationTest, resolved_op2_works) {
    auto a = TypifyOp2::resolve(Add::f, [](auto t){ return test_op2<typename decltype(t)::type>(Add::f, 2.0, 5.0, true); });
    auto b = TypifyOp2::resolve(Mul::f, [](auto t){ return test_op2<typename decltype(t)::type>(Mul::f, 5.0, 3.0, true); });
    auto c = TypifyOp2::resolve(Sub::f, [](auto t){ return test_op2<typename decltype(t)::type>(Sub::f, 8.0, 5.0, false); });
    // putting the lambda inside the EXPECT does not work
    EXPECT_EQ(a, 7.0);
    EXPECT_EQ(b, 15.0);
    EXPECT_EQ(c, 3.0);
}

GTEST_MAIN_RUN_ALL_TESTS()
