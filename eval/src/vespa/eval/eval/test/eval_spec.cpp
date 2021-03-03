// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "eval_spec.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/vespalib/util/string_hash.h>
#include <cmath>
#include <limits>

namespace vespalib::eval::test {

constexpr double my_nan = std::numeric_limits<double>::quiet_NaN();
constexpr double my_inf = std::numeric_limits<double>::infinity();

EvalSpec::Expression &EvalSpec::Expression::add_case(std::initializer_list<double> param_values,
                                                     double expected_result) {
    assert(param_values.size() == param_names.size());
    cases.emplace_back(param_values, expected_result);
    return *this;
}

EvalSpec::Expression &EvalSpec::Expression::add_cases(std::initializer_list<double> a_values, fun_1_ref fun) {
    for (double a: a_values) {
        add_case({a}, fun(a));
    }
    return *this;
}

EvalSpec::Expression &EvalSpec::Expression::add_cases(std::initializer_list<double> a_values,
                                                      std::initializer_list<double> b_values,
                                                      fun_2_ref fun) {
    for (double a: a_values) {
        for (double b: b_values) {
            add_case({a, b}, fun(a, b));
        }
    }
    return *this;
}

void EvalSpec::add_rule(const ParamSpec &a_spec, const vespalib::string &expression, fun_1_ref ref) {
    Expression &expr = add_expression({a_spec.name}, expression);
    std::vector<double> a_values = a_spec.expand(7);
    for (double a: a_values) {
        expr.add_case({a}, ref(a));
    }
}

void EvalSpec::add_rule(const ParamSpec &a_spec, const ParamSpec &b_spec,
                        const vespalib::string &expression, fun_2_ref ref) {
    Expression &expr = add_expression({a_spec.name, b_spec.name}, expression);
    std::vector<double> a_values = a_spec.expand(5);
    std::vector<double> b_values = b_spec.expand(5);
    for (double a: a_values) {
        for (double b: b_values) {
            expr.add_case({a, b}, ref(a, b));
        }
    }
}

vespalib::string
EvalSpec::EvalTest::as_string(const std::vector<vespalib::string> &param_names,
                              const std::vector<double> &param_values,
                              const vespalib::string &expression)
{
    assert(param_values.size() == param_names.size());
    vespalib::string str;
    str += "f(";
    for (size_t i = 0; i < param_names.size(); ++i) {
        if (i > 0) {
            str += ", ";
        }
        str += param_names[i];
        str += "=";
        str += make_string("%g", param_values[i]);
    }
    str += ") { ";
    str += expression;
    str += " }";
    return str;
}

bool
EvalSpec::EvalTest::is_same(double expected, double actual) {
    if (std::isnan(expected)) {
        return std::isnan(actual);
    }
    return (actual == expected);
}

void
EvalSpec::add_terminal_cases() {
    add_expression({}, "(-100)").add_case({}, -100.0);
    add_expression({}, "(-10)").add_case({}, -10.0);
    add_expression({}, "(-5.75)").add_case({}, -5.75);
    add_expression({}, "(-4.5)").add_case({}, -4.5);
    add_expression({}, "(-3)").add_case({}, -3.0);
    add_expression({}, "(-2)").add_case({}, -2.0);
    add_expression({}, "(-0.1)").add_case({}, -0.1);
    add_expression({}, "0").add_case({}, 0.0);
    add_expression({}, "0.1").add_case({}, 0.1);
    add_expression({}, "2").add_case({}, 2.0);
    add_expression({}, "3").add_case({}, 3.0);
    add_expression({}, "4.5").add_case({}, 4.5);
    add_expression({}, "5.75").add_case({}, 5.75);
    add_expression({}, "10").add_case({}, 10.0);
    add_expression({}, "100").add_case({}, 100.0);
    add_rule({"a", -5.0, 5.0}, "a", [](double a){ return a; });
    add_expression({}, "\"\"").add_case({}, vespalib::hash_code(""));
    add_expression({}, "\"foo\"").add_case({}, vespalib::hash_code("foo"));
    add_expression({}, "\"foo bar baz\"").add_case({}, vespalib::hash_code("foo bar baz"));
    add_expression({}, "\">\\\\\\\"\\t\\n\\r\\f<\"").add_case({}, vespalib::hash_code(">\\\"\t\n\r\f<"));
    add_expression({}, "\">\\x08\\x10\\x12\\x14<\"").add_case({}, vespalib::hash_code(">\x08\x10\x12\x14<"));
}

void
EvalSpec::add_arithmetic_cases() {
    add_rule({"a", -5.0, 5.0}, "(-a)", [](double a){ return -a; });
    add_rule({"a", -5.0, 5.0}, {"b", -5.0, 5.0}, "(a+b)", [](double a, double b){ return (a + b); });
    add_rule({"a", -5.0, 5.0}, {"b", -5.0, 5.0}, "(a-b)", [](double a, double b){ return (a - b); });
    add_rule({"a", -5.0, 5.0}, {"b", -5.0, 5.0}, "(a*b)", [](double a, double b){ return (a * b); });
    add_rule({"a", -5.0, 5.0}, {"b", -5.0, 5.0}, "(a/b)", [](double a, double b){ return (a / b); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "(a%b)", [](double a, double b){ return std::fmod(a, b); });
    add_rule({"a", -5.0, 5.0}, {"b", -5.0, 5.0}, "(a^b)", [](double a, double b){ return std::pow(a,b); });
    add_expression({"a", "b", "c", "d"}, "(((a+1)*(b-1))/((c+1)/(d-1)))")
        .add_case({0.0, 2.0, 0.0, 2.0}, 1.0)
        .add_case({1.0, 3.0, 0.0, 2.0}, 4.0)
        .add_case({1.0, 3.0, 1.0, 2.0}, 2.0)
        .add_case({1.0, 3.0, 1.0, 5.0}, 8.0);
}

void
EvalSpec::add_function_call_cases() {
    add_rule({"a", -1.0, 1.0}, "cos(a)", [](double a){ return std::cos(a); });
    add_rule({"a", -1.0, 1.0}, "sin(a)", [](double a){ return std::sin(a); });
    add_rule({"a", -1.0, 1.0}, "tan(a)", [](double a){ return std::tan(a); });
    add_rule({"a", -1.0, 1.0}, "cosh(a)", [](double a){ return std::cosh(a); });
    add_rule({"a", -1.0, 1.0}, "sinh(a)", [](double a){ return std::sinh(a); });
    add_rule({"a", -1.0, 1.0}, "tanh(a)", [](double a){ return std::tanh(a); });
    add_rule({"a", -1.0, 1.0}, "acos(a)", [](double a){ return std::acos(a); });
    add_rule({"a", -1.0, 1.0}, "asin(a)", [](double a){ return std::asin(a); });
    add_rule({"a", -1.0, 1.0}, "atan(a)", [](double a){ return std::atan(a); });
    add_rule({"a", -1.0, 1.0}, "exp(a)", [](double a){ return std::exp(a); });
    add_rule({"a", -1.0, 1.0}, "log10(a)", [](double a){ return std::log10(a); });
    add_rule({"a", -1.0, 1.0}, "log(a)", [](double a){ return std::log(a); });
    add_rule({"a", -1.0, 1.0}, "sqrt(a)", [](double a){ return std::sqrt(a); });
    add_rule({"a", -1.0, 1.0}, "ceil(a)", [](double a){ return std::ceil(a); });
    add_rule({"a", -1.0, 1.0}, "fabs(a)", [](double a){ return std::fabs(a); });
    add_rule({"a", -1.0, 1.0}, "floor(a)", [](double a){ return std::floor(a); });
    add_expression({"a"}, "isNan(a)")
        .add_case({-1.0}, 0.0).add_case({-0.5}, 0.0).add_case({0.0}, 0.0).add_case({0.5}, 0.0).add_case({1.0}, 0.0)
        .add_case({my_nan}, 1.0).add_case({my_inf}, 0.0).add_case({-my_inf}, 0.0);
    add_rule({"a", -1.0, 1.0}, "relu(a)", [](double a){ return std::max(a, 0.0); });
    add_rule({"a", -1.0, 1.0}, "sigmoid(a)", [](double a){ return 1.0 / (1.0 + std::exp(-1.0 * a)); });
    add_rule({"a", -1.0, 1.0}, "elu(a)", [](double a){ return (a < 0) ? std::exp(a)-1 : a; });
    add_rule({"a", -1.0, 1.0}, "erf(a)", [](double a){ return std::erf(a); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "atan2(a,b)", [](double a, double b){ return std::atan2(a, b); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "ldexp(a,b)", [](double a, double b){ return std::ldexp(a, b); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "pow(a,b)", [](double a, double b){ return std::pow(a, b); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "fmod(a,b)", [](double a, double b){ return std::fmod(a, b); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "min(a,b)", [](double a, double b){ return std::min(a, b); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "max(a,b)", [](double a, double b){ return std::max(a, b); });
}

void
EvalSpec::add_tensor_operation_cases() {
    add_rule({"a", -1.0, 1.0}, "map(a,f(x)(sin(x)))", [](double x){ return std::sin(x); });
    add_rule({"a", -1.0, 1.0}, "map(a,f(x)(x*x*3))", [](double x){ return ((x * x) * 3); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "join(a,b,f(x,y)(x+y))", [](double x, double y){ return (x + y); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "join(a,b,f(x,y)(x*y*3))", [](double x, double y){ return ((x * y) * 3); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "merge(a,b,f(x,y)(x+y))", [](double x, double y){ return (x + y); });
    add_rule({"a", -1.0, 1.0}, {"b", -1.0, 1.0}, "merge(a,b,f(x,y)(x*y*3))", [](double x, double y){ return ((x * y) * 3); });
    add_rule({"a", -1.0, 1.0}, "reduce(a,avg)", [](double a){ return a; });
    add_rule({"a", -1.0, 1.0}, "reduce(a,count)", [](double){ return 1.0; });
    add_rule({"a", -1.0, 1.0}, "reduce(a,prod)", [](double a){ return a; });
    add_rule({"a", -1.0, 1.0}, "reduce(a,sum)", [](double a){ return a; });
    add_rule({"a", -1.0, 1.0}, "reduce(a,max)", [](double a){ return a; });
    add_rule({"a", -1.0, 1.0}, "reduce(a,median)", [](double a){ return a; });
    add_rule({"a", -1.0, 1.0}, "reduce(a,min)", [](double a){ return a; });
    add_expression({"a"}, "rename(a,x,y)");
    add_expression({"a"}, "rename(a,(x,y),(y,x))");
    add_expression({}, "tensor(x[10])(x)");
    add_expression({}, "tensor(x[10],y[10])(x==y)");
    add_expression({"a","b"}, "concat(a,b,x)");
    add_expression({"a","b"}, "concat(a,b,y)");
    add_expression({"a"}, "cell_cast(a,float)");
    add_expression({}, "tensor(x[3]):{{x:0}:0,{x:1}:1,{x:2}:2}");
    add_expression({"a"}, "a{x:3}");
}

void
EvalSpec::add_comparison_cases() {
    add_expression({"a", "b"}, "(a==b)")
        .add_case({my_nan, 2.0},      0.0)
        .add_case({2.0, my_nan},      0.0)
        .add_case({my_nan, my_nan},   0.0)
        .add_case({1.0, 2.0},         0.0)
        .add_case({2.0 - 1e-10, 2.0}, 0.0)
        .add_case({2.0, 2.0},         1.0)
        .add_case({2.0 + 1e-10, 2.0}, 0.0)
        .add_case({3.0, 2.0},         0.0);

    add_expression({"a", "b"}, "(a!=b)")
        .add_case({my_nan, 2.0},      1.0)
        .add_case({2.0, my_nan},      1.0)
        .add_case({my_nan, my_nan},   1.0)
        .add_case({1.0, 2.0},         1.0)
        .add_case({2.0 - 1e-10, 2.0}, 1.0)
        .add_case({2.0, 2.0},         0.0)
        .add_case({2.0 + 1e-10, 2.0}, 1.0)
        .add_case({3.0, 2.0},         1.0);

    add_expression({"a", "b"}, "(a~=b)")
        .add_case({my_nan, 2.0},      0.0)
        .add_case({2.0, my_nan},      0.0)
        .add_case({my_nan, my_nan},   0.0)
        .add_case({0.5, 0.5},         1.0)
        .add_case({1.0, 2.0},         0.0)
        .add_case({2.0, 2.0},         1.0)
        .add_case({3.0, 2.0},         0.0)
        .add_case({0.5 - 1e-10, 0.5}, 1.0)
        .add_case({0.5, 0.5 - 1e-10}, 1.0)
        .add_case({2.0 - 1e-10, 2.0}, 1.0)
        .add_case({2.0, 2.0 - 1e-10}, 1.0)
        .add_case({0.5 + 1e-10, 0.5}, 1.0)
        .add_case({0.5, 0.5 + 1e-10}, 1.0)
        .add_case({2.0 + 1e-10, 2.0}, 1.0)
        .add_case({2.0, 2.0 + 1e-10}, 1.0)
        .add_case({0.5 - 2e-7, 0.5},  0.0)
        .add_case({0.5, 0.5 - 2e-7},  0.0)
        .add_case({2.0 - 5e-7, 2.0},  0.0)
        .add_case({2.0, 2.0 - 5e-7},  0.0)
        .add_case({0.5 + 2e-7, 0.5},  0.0)
        .add_case({0.5, 0.5 + 2e-7},  0.0)
        .add_case({2.0 + 5e-7, 2.0},  0.0)
        .add_case({2.0, 2.0 + 5e-7},  0.0);

    add_expression({"a", "b"}, "(a<b)")
        .add_case({my_nan, 2.0},      0.0)
        .add_case({2.0, my_nan},      0.0)
        .add_case({my_nan, my_nan},   0.0)
        .add_case({1.0, 2.0},         1.0)
        .add_case({2.0 - 1e-10, 2.0}, 1.0)
        .add_case({2.0, 2.0},         0.0)
        .add_case({2.0 + 1e-10, 2.0}, 0.0)
        .add_case({3.0, 2.0},         0.0);

    add_expression({"a", "b"}, "(a<=b)")
        .add_case({my_nan, 2.0},      0.0)
        .add_case({2.0, my_nan},      0.0)
        .add_case({my_nan, my_nan},   0.0)
        .add_case({1.0, 2.0},         1.0)
        .add_case({2.0 - 1e-10, 2.0}, 1.0)
        .add_case({2.0, 2.0},         1.0)
        .add_case({2.0 + 1e-10, 2.0}, 0.0)
        .add_case({3.0, 2.0},         0.0);

    add_expression({"a", "b"}, "(a>b)")
        .add_case({my_nan, 2.0},      0.0)
        .add_case({2.0, my_nan},      0.0)
        .add_case({my_nan, my_nan},   0.0)
        .add_case({1.0, 2.0},         0.0)
        .add_case({2.0 - 1e-10, 2.0}, 0.0)
        .add_case({2.0, 2.0},         0.0)
        .add_case({2.0 + 1e-10, 2.0}, 1.0)
        .add_case({3.0, 2.0},         1.0);

    add_expression({"a", "b"}, "(a>=b)")
        .add_case({my_nan, 2.0},      0.0)
        .add_case({2.0, my_nan},      0.0)
        .add_case({my_nan, my_nan},   0.0)
        .add_case({1.0, 2.0},         0.0)
        .add_case({2.0 - 1e-10, 2.0}, 0.0)
        .add_case({2.0, 2.0},         1.0)
        .add_case({2.0 + 1e-10, 2.0}, 1.0)
        .add_case({3.0, 2.0},         1.0);
}

void
EvalSpec::add_set_membership_cases()
{
    add_expression({"a"}, "(a in [])")
        .add_case({0.0}, 0.0)
        .add_case({1.0}, 0.0);

    add_expression({"a"}, "(a in [2.0])")
        .add_case({my_nan},      0.0)
        .add_case({1.0},         0.0)
        .add_case({2.0 - 1e-10}, 0.0)
        .add_case({2.0},         1.0)
        .add_case({2.0 + 1e-10}, 0.0)
        .add_case({3.0},         0.0);

    add_expression({"a"}, "(a in [10,20,30])")
        .add_case({0.0},  0.0)
        .add_case({3.0},  0.0)
        .add_case({10.0}, 1.0)
        .add_case({20.0}, 1.0)
        .add_case({30.0}, 1.0);

    add_expression({"a"}, "(a in [30,20,10])")
        .add_case({10.0}, 1.0)
        .add_case({20.0}, 1.0)
        .add_case({30.0}, 1.0);
}

void
EvalSpec::add_boolean_cases() {
    add_expression({"a"}, "(!a)")
        .add_cases({my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   [](double a)->double{ return !bool(a); });

    add_expression({"a"}, "(!(!a))")
        .add_cases({my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   [](double a)->double{ return bool(a); });

    add_expression({"a", "b"}, "(a&&b)")
        .add_cases({my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   {my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   [](double a, double b)->double{ return (bool(a) && bool(b)); });

    add_expression({"a", "b"}, "(a||b)")
        .add_cases({my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   {my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   [](double a, double b)->double{ return (bool(a) || bool(b)); });
}

void
EvalSpec::add_if_cases() {
    add_expression({"a"}, "if(a,1,0)")
        .add_cases({my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   [](double a){ if (a) { return 1.0; } else { return 0.0; } });

    add_expression({"a", "b"}, "if(a,if(b,1,2),if(b,3,4))")
        .add_cases({my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   {my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   [](double a, double b)
                   {
                       if (a) {
                           if (b) {
                               return 1.0;
                           } else {
                               return 2.0;
                           }
                       } else {
                           if (b) {
                               return 3.0;
                           } else {
                               return 4.0;
                           }
                       }
                   });
    add_expression({"a"}, "if(a,1,0,0.25)")
        .add_cases({my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   [](double a){ if (a) { return 1.0; } else { return 0.0; } });
    add_expression({"a"}, "if(a,1,0,0.75)")
        .add_cases({my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   [](double a){ if (a) { return 1.0; } else { return 0.0; } });
}

void
EvalSpec::add_complex_cases() {
    add_expression({"a", "b"}, "((a<3)||b)")
        .add_cases({2.0, 4.0}, {0.0, 0.5, 1.0},
                   [](double a, double b)->double{ return ((a < 3) || bool(b)); });

    add_expression({"a", "b"}, "((a<3)==b)")
        .add_cases({2.0, 4.0}, {0.0, 0.5, 1.0},
                   [](double a, double b)->double{ return (double((a < 3)) == b); });

    add_expression({"a"}, "(!(-a))")
        .add_cases({my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   [](double a)->double{ return !bool(-a); });

    add_expression({"a"}, "(-(!a))")
        .add_cases({my_nan, -my_inf, -123.0, -1.0, -0.001, 0.0, 0.001, 1.0, 123.0, my_inf},
                   [](double a)->double{ return -double(!bool(a)); });
}

}
