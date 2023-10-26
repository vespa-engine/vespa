// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operation.h"
#include "function.h"
#include "key_gen.h"
#include "extract_bit.h"
#include "hamming_distance.h"
#include <vespa/vespalib/util/approx.h>
#include <algorithm>

namespace vespalib::eval::operation {

double Neg::f(double a) { return -a; }
double Not::f(double a) { return (a != 0.0) ? 0.0 : 1.0; }
double Add::f(double a, double b) { return (a + b); }
double Sub::f(double a, double b) { return (a - b); }
double Mul::f(double a, double b) { return (a * b); }
double Div::f(double a, double b) { return (a / b); }
double Mod::f(double a, double b) { return std::fmod(a, b); }
double Pow::f(double a, double b) { return std::pow(a, b); }
double Equal::f(double a, double b) { return (a == b) ? 1.0 : 0.0; }
double NotEqual::f(double a, double b) { return (a != b) ? 1.0 : 0.0; }
double Approx::f(double a, double b) { return approx_equal(a, b); }
double Less::f(double a, double b) { return (a < b) ? 1.0 : 0.0; }
double LessEqual::f(double a, double b) { return (a <= b) ? 1.0 : 0.0; }
double Greater::f(double a, double b) { return (a > b) ? 1.0 : 0.0; }
double GreaterEqual::f(double a, double b) { return (a >= b) ? 1.0 : 0.0; }
double And::f(double a, double b) { return ((a != 0.0) && (b != 0.0)) ? 1.0 : 0.0; }
double Or::f(double a, double b) { return ((a != 0.0) || (b != 0.0)) ? 1.0 : 0.0; }
double Cos::f(double a) { return std::cos(a); }
double Sin::f(double a) { return std::sin(a); }
double Tan::f(double a) { return std::tan(a); }
double Cosh::f(double a) { return std::cosh(a); }
double Sinh::f(double a) { return std::sinh(a); }
double Tanh::f(double a) { return std::tanh(a); }
double Acos::f(double a) { return std::acos(a); }
double Asin::f(double a) { return std::asin(a); }
double Atan::f(double a) { return std::atan(a); }
double Exp::f(double a) { return std::exp(a); }
double Log10::f(double a) { return std::log10(a); }
double Log::f(double a) { return std::log(a); }
double Sqrt::f(double a) { return std::sqrt(a); }
double Ceil::f(double a) { return std::ceil(a); }
double Fabs::f(double a) { return std::fabs(a); }
double Floor::f(double a) { return std::floor(a); }
double Atan2::f(double a, double b) { return std::atan2(a, b); }
double Ldexp::f(double a, double b) { return std::ldexp(a, b); }
double Min::f(double a, double b) { return std::min(a, b); }
double Max::f(double a, double b) { return std::max(a, b); }
double IsNan::f(double a) { return std::isnan(a) ? 1.0 : 0.0; }
double Relu::f(double a) { return std::max(a, 0.0); }
double Sigmoid::f(double a) { return 1.0 / (1.0 + std::exp(-1.0 * a)); }
double Elu::f(double a) { return (a < 0) ? std::exp(a) - 1 : a; }
double Erf::f(double a) { return std::erf(a); }
double Bit::f(double a, double b) { return extract_bit(a, b); }
double Hamming::f(double a, double b) { return hamming_distance(a, b); }
//-----------------------------------------------------------------------------
double Inv::f(double a) { return (1.0 / a); }
double Square::f(double a) { return (a * a); }
double Cube::f(double a) { return (a * a * a); }

namespace {

template <typename T>
void add_op(std::map<vespalib::string,T> &map, const Function &fun, T op) {
    assert(!fun.has_error());
    auto key = gen_key(fun, PassParams::SEPARATE);
    auto res = map.emplace(key, op);
    assert(res.second);
}

template <typename T>
std::optional<T> lookup_op(const std::map<vespalib::string,T> &map, const Function &fun) {
    auto key = gen_key(fun, PassParams::SEPARATE);
    auto pos = map.find(key);
    if (pos != map.end()) {
        return pos->second;
    }
    return std::nullopt;
}

void add_op1(std::map<vespalib::string,op1_t> &map, const vespalib::string &expr, op1_t op) {
    add_op(map, *Function::parse({"a"}, expr), op);
}

void add_op2(std::map<vespalib::string,op2_t> &map, const vespalib::string &expr, op2_t op) {
    add_op(map, *Function::parse({"a", "b"}, expr), op);
}

std::map<vespalib::string,op1_t> make_op1_map() {
    std::map<vespalib::string,op1_t> map;
    add_op1(map, "-a",         Neg::f);
    add_op1(map, "!a",         Not::f);
    add_op1(map, "cos(a)",     Cos::f);
    add_op1(map, "sin(a)",     Sin::f);
    add_op1(map, "tan(a)",     Tan::f);
    add_op1(map, "cosh(a)",    Cosh::f);
    add_op1(map, "sinh(a)",    Sinh::f);
    add_op1(map, "tanh(a)",    Tanh::f);
    add_op1(map, "acos(a)",    Acos::f);
    add_op1(map, "asin(a)",    Asin::f);
    add_op1(map, "atan(a)",    Atan::f);
    add_op1(map, "exp(a)",     Exp::f);
    add_op1(map, "log10(a)",   Log10::f);
    add_op1(map, "log(a)",     Log::f);
    add_op1(map, "sqrt(a)",    Sqrt::f);
    add_op1(map, "ceil(a)",    Ceil::f);
    add_op1(map, "fabs(a)",    Fabs::f);
    add_op1(map, "floor(a)",   Floor::f);
    add_op1(map, "isNan(a)",   IsNan::f);
    add_op1(map, "relu(a)",    Relu::f);
    add_op1(map, "sigmoid(a)", Sigmoid::f);
    add_op1(map, "elu(a)",     Elu::f);
    add_op1(map, "erf(a)",     Erf::f);
    //-------------------------------------
    add_op1(map, "1/a",        Inv::f);
    add_op1(map, "a*a",        Square::f);
    add_op1(map, "a^2",        Square::f);
    add_op1(map, "pow(a,2)",   Square::f);
    add_op1(map, "(a*a)*a",    Cube::f);
    add_op1(map, "a*(a*a)",    Cube::f);
    add_op1(map, "a^3",        Cube::f);
    add_op1(map, "pow(a,3)",   Cube::f);
    return map;
}

std::map<vespalib::string,op2_t> make_op2_map() {
    std::map<vespalib::string,op2_t> map;
    add_op2(map, "a+b",        Add::f);
    add_op2(map, "a-b",        Sub::f);
    add_op2(map, "a*b",        Mul::f);
    add_op2(map, "a/b",        Div::f);
    add_op2(map, "a%b",        Mod::f);
    add_op2(map, "a^b",        Pow::f);
    add_op2(map, "a==b",       Equal::f);
    add_op2(map, "a!=b",       NotEqual::f);
    add_op2(map, "a~=b",       Approx::f);
    add_op2(map, "a<b",        Less::f);
    add_op2(map, "a<=b",       LessEqual::f);
    add_op2(map, "a>b",        Greater::f);
    add_op2(map, "a>=b",       GreaterEqual::f);
    add_op2(map, "a&&b",       And::f);
    add_op2(map, "a||b",       Or::f);
    add_op2(map, "atan2(a,b)", Atan2::f);
    add_op2(map, "ldexp(a,b)", Ldexp::f);
    add_op2(map, "pow(a,b)",   Pow::f);
    add_op2(map, "fmod(a,b)",  Mod::f);
    add_op2(map, "min(a,b)",   Min::f);
    add_op2(map, "max(a,b)",   Max::f);
    add_op2(map, "bit(a,b)",   Bit::f);
    add_op2(map, "hamming(a,b)", Hamming::f);
    return map;
}

} // namespace <unnamed>

std::optional<op1_t> lookup_op1(const Function &fun) {
    static const std::map<vespalib::string,op1_t> map = make_op1_map();
    return lookup_op(map, fun);
}

std::optional<op2_t> lookup_op2(const Function &fun) {
    static const std::map<vespalib::string,op2_t> map = make_op2_map();
    return lookup_op(map, fun);
}

}
