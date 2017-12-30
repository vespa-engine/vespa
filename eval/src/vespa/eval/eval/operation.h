// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace vespalib::eval::operation {

struct Neg { static double f(double a); };
struct Not { static double f(double a); };
struct Add { static double f(double a, double b); };
struct Sub { static double f(double a, double b); };
struct Mul { static double f(double a, double b); };
struct Div { static double f(double a, double b); };
struct Mod { static double f(double a, double b); };
struct Pow { static double f(double a, double b); };
struct Equal { static double f(double a, double b); };
struct NotEqual { static double f(double a, double b); };
struct Approx { static double f(double a, double b); };
struct Less { static double f(double a, double b); };
struct LessEqual { static double f(double a, double b); };
struct Greater { static double f(double a, double b); };
struct GreaterEqual { static double f(double a, double b); };
struct And { static double f(double a, double b); };
struct Or { static double f(double a, double b); };
struct Cos { static double f(double a); };
struct Sin { static double f(double a); };
struct Tan { static double f(double a); };
struct Cosh { static double f(double a); };
struct Sinh { static double f(double a); };
struct Tanh { static double f(double a); };
struct Acos { static double f(double a); };
struct Asin { static double f(double a); };
struct Atan { static double f(double a); };
struct Exp { static double f(double a); };
struct Log10 { static double f(double a); };
struct Log { static double f(double a); };
struct Sqrt { static double f(double a); };
struct Ceil { static double f(double a); };
struct Fabs { static double f(double a); };
struct Floor { static double f(double a); };
struct Atan2 { static double f(double a, double b); };
struct Ldexp { static double f(double a, double b); };
struct Min { static double f(double a, double b); };
struct Max { static double f(double a, double b); };
struct IsNan { static double f(double a); };
struct Relu { static double f(double a); };
struct Sigmoid { static double f(double a); };
struct Elu { static double f(double a); };

}
