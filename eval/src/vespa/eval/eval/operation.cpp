// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operation.h"
#include <vespa/vespalib/util/approx.h>

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

}
