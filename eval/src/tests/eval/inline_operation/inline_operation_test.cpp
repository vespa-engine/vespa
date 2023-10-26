// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/operation.h>
#include <vespa/eval/eval/inline_operation.h>
#include <vespa/eval/eval/function.h>
#include <vespa/vespalib/util/typify.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::typify_invoke;
using namespace vespalib::eval;
using namespace vespalib::eval::operation;

const int my_value = 42;
struct AsValue { template <typename T> static int invoke() { return my_value; } };
struct AsRef { template <typename T> static const int &invoke() { return my_value; } };

template <typename T> void test_op1(op1_t ref, double a, double expect) {
    bool need_ref = std::is_same_v<T,CallOp1>;
    T op = need_ref ? T(ref) : T(nullptr);
    EXPECT_DOUBLE_EQ(ref(a), expect);
    EXPECT_DOUBLE_EQ(op(a), expect);
};

template <typename T> void test_op2(op2_t ref, double a, double b, double expect) {
    bool need_ref = std::is_same_v<T,CallOp2>;
    T op = need_ref ? T(ref) : T(nullptr);
    EXPECT_DOUBLE_EQ(ref(a, b), expect);
    EXPECT_DOUBLE_EQ(op(a, b), expect);
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
    EXPECT_EQ(as_op1("-a"),         &Neg::f);
    EXPECT_EQ(as_op1("!a"),         &Not::f);
    EXPECT_EQ(as_op1("cos(a)"),     &Cos::f);
    EXPECT_EQ(as_op1("sin(a)"),     &Sin::f);
    EXPECT_EQ(as_op1("tan(a)"),     &Tan::f);
    EXPECT_EQ(as_op1("cosh(a)"),    &Cosh::f);
    EXPECT_EQ(as_op1("sinh(a)"),    &Sinh::f);
    EXPECT_EQ(as_op1("tanh(a)"),    &Tanh::f);
    EXPECT_EQ(as_op1("acos(a)"),    &Acos::f);
    EXPECT_EQ(as_op1("asin(a)"),    &Asin::f);
    EXPECT_EQ(as_op1("atan(a)"),    &Atan::f);
    EXPECT_EQ(as_op1("exp(a)"),     &Exp::f);
    EXPECT_EQ(as_op1("log10(a)"),   &Log10::f);
    EXPECT_EQ(as_op1("log(a)"),     &Log::f);
    EXPECT_EQ(as_op1("sqrt(a)"),    &Sqrt::f);
    EXPECT_EQ(as_op1("ceil(a)"),    &Ceil::f);
    EXPECT_EQ(as_op1("fabs(a)"),    &Fabs::f);
    EXPECT_EQ(as_op1("floor(a)"),   &Floor::f);
    EXPECT_EQ(as_op1("isNan(a)"),   &IsNan::f);
    EXPECT_EQ(as_op1("relu(a)"),    &Relu::f);
    EXPECT_EQ(as_op1("sigmoid(a)"), &Sigmoid::f);
    EXPECT_EQ(as_op1("elu(a)"),     &Elu::f);
    EXPECT_EQ(as_op1("erf(a)"),     &Erf::f);
    //-------------------------------------------
    EXPECT_EQ(as_op1("1/a"),        &Inv::f);
    EXPECT_EQ(as_op1("1.0/a"),      &Inv::f);
    EXPECT_EQ(as_op1("a*a"),        &Square::f);
    EXPECT_EQ(as_op1("a^2"),        &Square::f);
    EXPECT_EQ(as_op1("a^2.0"),      &Square::f);
    EXPECT_EQ(as_op1("pow(a,2)"),   &Square::f);
    EXPECT_EQ(as_op1("pow(a,2.0)"), &Square::f);
    EXPECT_EQ(as_op1("a*a*a"),      &Cube::f);
    EXPECT_EQ(as_op1("(a*a)*a"),    &Cube::f);
    EXPECT_EQ(as_op1("a*(a*a)"),    &Cube::f);
    EXPECT_EQ(as_op1("a^3"),        &Cube::f);
    EXPECT_EQ(as_op1("a^3.0"),      &Cube::f);
    EXPECT_EQ(as_op1("pow(a,3)"),   &Cube::f);
    EXPECT_EQ(as_op1("pow(a,3.0)"), &Cube::f);
}

TEST(InlineOperationTest, op1_lambdas_are_recognized_with_different_parameter_names) {
    EXPECT_EQ(lookup_op1(*Function::parse({"x"}, "-x")).value(), &Neg::f);
    EXPECT_EQ(lookup_op1(*Function::parse({"x"}, "!x")).value(), &Not::f);
}

TEST(InlineOperationTest, non_op1_lambdas_are_not_recognized) {
    EXPECT_FALSE(lookup_op1(*Function::parse({"a"}, "a*a+3")).has_value());
    EXPECT_FALSE(lookup_op1(*Function::parse({"a", "b"}, "a+b")).has_value());
}

TEST(InlineOperationTest, op2_lambdas_are_recognized) {
    EXPECT_EQ(as_op2("a+b"),        &Add::f);
    EXPECT_EQ(as_op2("a-b"),        &Sub::f);
    EXPECT_EQ(as_op2("a*b"),        &Mul::f);
    EXPECT_EQ(as_op2("a/b"),        &Div::f);
    EXPECT_EQ(as_op2("a%b"),        &Mod::f);
    EXPECT_EQ(as_op2("a^b"),        &Pow::f);
    EXPECT_EQ(as_op2("a==b"),       &Equal::f);
    EXPECT_EQ(as_op2("a!=b"),       &NotEqual::f);
    EXPECT_EQ(as_op2("a~=b"),       &Approx::f);
    EXPECT_EQ(as_op2("a<b"),        &Less::f);
    EXPECT_EQ(as_op2("a<=b"),       &LessEqual::f);
    EXPECT_EQ(as_op2("a>b"),        &Greater::f);
    EXPECT_EQ(as_op2("a>=b"),       &GreaterEqual::f);
    EXPECT_EQ(as_op2("a&&b"),       &And::f);
    EXPECT_EQ(as_op2("a||b"),       &Or::f);
    EXPECT_EQ(as_op2("atan2(a,b)"), &Atan2::f);
    EXPECT_EQ(as_op2("ldexp(a,b)"), &Ldexp::f);
    EXPECT_EQ(as_op2("pow(a,b)"),   &Pow::f);
    EXPECT_EQ(as_op2("fmod(a,b)"),  &Mod::f);
    EXPECT_EQ(as_op2("min(a,b)"),   &Min::f);
    EXPECT_EQ(as_op2("max(a,b)"),   &Max::f);
    EXPECT_EQ(as_op2("bit(a,b)"),   &Bit::f);
    EXPECT_EQ(as_op2("hamming(a,b)"), &Hamming::f);
}

TEST(InlineOperationTest, op2_lambdas_are_recognized_with_different_parameter_names) {
    EXPECT_EQ(lookup_op2(*Function::parse({"x", "y"}, "x+y")).value(), &Add::f);
    EXPECT_EQ(lookup_op2(*Function::parse({"x", "y"}, "x-y")).value(), &Sub::f);
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

TEST(InlineOperationTest, op1_typifier_forwards_return_value_correctly) {
    auto a = typify_invoke<1,TypifyOp1,AsValue>(Neg::f);
    auto b = typify_invoke<1,TypifyOp1,AsRef>(Neg::f);
    EXPECT_EQ(a, my_value);
    EXPECT_EQ(b, my_value);
    bool same_memory = (&(typify_invoke<1,TypifyOp1,AsRef>(Neg::f)) == &my_value);
    EXPECT_EQ(same_memory, true);
}

TEST(InlineOperationTest, op2_typifier_forwards_return_value_correctly) {
    auto a = typify_invoke<1,TypifyOp2,AsValue>(Add::f);
    auto b = typify_invoke<1,TypifyOp2,AsRef>(Add::f);
    EXPECT_EQ(a, my_value);
    EXPECT_EQ(b, my_value);
    bool same_memory = (&(typify_invoke<1,TypifyOp2,AsRef>(Add::f)) == &my_value);
    EXPECT_EQ(same_memory, true);
}

TEST(InlineOperationTest, inline_op1_example_works) {
    op1_t ignored = nullptr;
    InlineOp1<Inv> op(ignored);
    EXPECT_EQ(op(2.0), 0.5);
    EXPECT_EQ(op(4.0f), 0.25f);
    EXPECT_EQ(op(8.0), 0.125);
}

TEST(InlineOperationTest, inline_op2_example_works) {
    op2_t ignored = nullptr;
    InlineOp2<Add> op(ignored);
    EXPECT_EQ(op(2.0, 3.0), 5.0);
    EXPECT_EQ(op(3.0, 7.0), 10.0);
}

TEST(InlineOperationTest, parameter_swap_wrapper_works) {
    CallOp2 op(Sub::f);
    SwapArgs2<CallOp2> swap_op(Sub::f);
    EXPECT_EQ(op(2,3), -1);
    EXPECT_EQ(swap_op(2,3), 1);
    EXPECT_EQ(op(3,7), -4);
    EXPECT_EQ(swap_op(3,7), 4);
}

//-----------------------------------------------------------------------------

TEST(InlineOperationTest, op1_cube_is_inlined) {
    TypifyOp1::resolve(Cube::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp1<Cube>>;
                           op1_t ref = Cube::f;
                           EXPECT_TRUE(type_ok);
                           test_op1<T>(ref, 2.0, 8.0);
                           test_op1<T>(ref, 3.0, 27.0);
                           test_op1<T>(ref, 7.0, 343.0);
                       });
}

TEST(InlineOperationTest, op1_exp_is_inlined) {
    TypifyOp1::resolve(Exp::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp1<Exp>>;
                           op1_t ref = Exp::f;
                           EXPECT_TRUE(type_ok);
                           test_op1<T>(ref, 2.0, std::exp(2.0));
                           test_op1<T>(ref, 3.0, std::exp(3.0));
                           test_op1<T>(ref, 7.0, std::exp(7.0));
                       });
}

TEST(InlineOperationTest, op1_inv_is_inlined) {
    TypifyOp1::resolve(Inv::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp1<Inv>>;
                           op1_t ref = Inv::f;
                           EXPECT_TRUE(type_ok);
                           test_op1<T>(ref, 2.0, 1.0/2.0);
                           test_op1<T>(ref, 4.0, 1.0/4.0);
                           test_op1<T>(ref, 8.0, 1.0/8.0);
                       });
}

TEST(InlineOperationTest, op1_sqrt_is_inlined) {
    TypifyOp1::resolve(Sqrt::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp1<Sqrt>>;
                           op1_t ref = Sqrt::f;
                           EXPECT_TRUE(type_ok);
                           test_op1<T>(ref, 2.0, sqrt(2.0));
                           test_op1<T>(ref, 4.0, sqrt(4.0));
                           test_op1<T>(ref, 64.0, sqrt(64.0));
                       });
}

TEST(InlineOperationTest, op1_square_is_inlined) {
    TypifyOp1::resolve(Square::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp1<Square>>;
                           op1_t ref = Square::f;
                           EXPECT_TRUE(type_ok);
                           test_op1<T>(ref, 2.0, 4.0);
                           test_op1<T>(ref, 3.0, 9.0);
                           test_op1<T>(ref, 7.0, 49.0);
                       });
}

TEST(InlineOperationTest, op1_tanh_is_inlined) {
    TypifyOp1::resolve(Tanh::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp1<Tanh>>;
                           op1_t ref = Tanh::f;
                           EXPECT_TRUE(type_ok);
                           test_op1<T>(ref, 0.1, std::tanh(0.1));
                           test_op1<T>(ref, 0.3, std::tanh(0.3));
                           test_op1<T>(ref, 0.7, std::tanh(0.7));
                       });
}

TEST(InlineOperationTest, op1_neg_is_not_inlined) {
    TypifyOp1::resolve(Neg::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,CallOp1>;
                           op1_t ref = Neg::f;
                           EXPECT_TRUE(type_ok);
                           test_op1<T>(ref, 3.0, -3.0);
                           test_op1<T>(ref, 5.0, -5.0);
                           test_op1<T>(ref, -2.0, 2.0);
                       });
}

//-----------------------------------------------------------------------------

TEST(InlineOperationTest, op2_add_is_inlined) {
    TypifyOp2::resolve(Add::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp2<Add>>;
                           op2_t ref = Add::f;
                           EXPECT_TRUE(type_ok);
                           test_op2<T>(ref, 2.0, 2.0,  4.0);
                           test_op2<T>(ref, 3.0, 8.0, 11.0);
                           test_op2<T>(ref, 7.0, 1.0,  8.0);
                       });
}

TEST(InlineOperationTest, op2_div_is_inlined) {
    TypifyOp2::resolve(Div::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp2<Div>>;
                           op2_t ref = Div::f;
                           EXPECT_TRUE(type_ok);
                           test_op2<T>(ref, 2.0, 2.0, 1.0);
                           test_op2<T>(ref, 3.0, 8.0, 3.0 / 8.0);
                           test_op2<T>(ref, 7.0, 5.0, 7.0 / 5.0);
                       });
}

TEST(InlineOperationTest, op2_mul_is_inlined) {
    TypifyOp2::resolve(Mul::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp2<Mul>>;
                           op2_t ref = Mul::f;
                           EXPECT_TRUE(type_ok);
                           test_op2<T>(ref, 2.0, 2.0, 4.0);
                           test_op2<T>(ref, 3.0, 8.0, 24.0);
                           test_op2<T>(ref, 7.0, 5.0, 35.0);
                       });
}

TEST(InlineOperationTest, op2_pow_is_inlined) {
    TypifyOp2::resolve(Pow::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp2<Pow>>;
                           op2_t ref = Pow::f;
                           EXPECT_TRUE(type_ok);
                           test_op2<T>(ref, 2.0, 2.0, std::pow(2.0, 2.0));
                           test_op2<T>(ref, 3.0, 8.0, std::pow(3.0, 8.0));
                           test_op2<T>(ref, 7.0, 5.0, std::pow(7.0, 5.0));
                       });
}

TEST(InlineOperationTest, op2_sub_is_inlined) {
    TypifyOp2::resolve(Sub::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,InlineOp2<Sub>>;
                           op2_t ref = Sub::f;
                           EXPECT_TRUE(type_ok);
                           test_op2<T>(ref, 3.0, 2.0, 1.0);
                           test_op2<T>(ref, 3.0, 8.0, -5.0);
                           test_op2<T>(ref, 7.0, 5.0, 2.0);
                       });
}

TEST(InlineOperationTest, op2_mod_is_not_inlined) {
    TypifyOp2::resolve(Mod::f, [](auto t)
                       {
                           using T = typename decltype(t)::type;
                           bool type_ok = std::is_same_v<T,CallOp2>;
                           op2_t ref = Mod::f;
                           EXPECT_TRUE(type_ok);
                           test_op2<T>(ref, 3.0, 2.0, std::fmod(3.0, 2.0));
                           test_op2<T>(ref, 3.0, 8.0, std::fmod(3.0, 8.0));
                           test_op2<T>(ref, 7.0, 5.0, std::fmod(7.0, 5.0));
                       });
}

GTEST_MAIN_RUN_ALL_TESTS()
