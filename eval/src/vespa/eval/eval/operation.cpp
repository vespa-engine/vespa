// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operation.h"
#include "value.h"
#include "operation_visitor.h"
#include <cmath>

namespace vespalib {
namespace eval {

const Value &
UnaryOperation::perform(const Value &lhs, Stash &stash) const {
    if (lhs.is_error()) {
        return stash.create<ErrorValue>();
    } else if (lhs.is_double()) {
        return stash.create<DoubleValue>(eval(lhs.as_double()));
    } else {
        return lhs.apply(*this, stash);
    }
}

const Value &
BinaryOperation::perform(const Value &lhs, const Value &rhs, Stash &stash) const {
    if (lhs.is_error() || rhs.is_error()) {
        return stash.create<ErrorValue>();
    } else if (lhs.is_double() && rhs.is_double()) {
        return stash.create<DoubleValue>(eval(lhs.as_double(), rhs.as_double()));
    } else if (lhs.is_double()) {
        BindLeft unary_op(*this, lhs.as_double());
        return rhs.apply(unary_op, stash);
    } else if (rhs.is_double()) {
        BindRight unary_op(*this, rhs.as_double());
        return lhs.apply(unary_op, stash);
    } else {
        return lhs.apply(*this, rhs, stash);
    }
}

template <typename T> void Op1<T>::accept(OperationVisitor &visitor) const {
    visitor.visit(static_cast<const T&>(*this));
}

template <typename T> void Op2<T>::accept(OperationVisitor &visitor) const {
    visitor.visit(static_cast<const T&>(*this));
}

template <typename T> std::unique_ptr<BinaryOperation> Op2<T>::clone() const {
    return std::make_unique<T>();
}

namespace operation {
double Neg::eval(double a) const { return -a; }
double Not::eval(double a) const { return (a != 0.0) ? 0.0 : 1.0; }
double Add::eval(double a, double b) const { return (a + b); }
double Sub::eval(double a, double b) const { return (a - b); }
double Mul::eval(double a, double b) const { return (a * b); }
double Div::eval(double a, double b) const { return (a / b); }
double Pow::eval(double a, double b) const { return std::pow(a, b); }
double Equal::eval(double a, double b) const { return (a == b) ? 1.0 : 0.0; }
double NotEqual::eval(double a, double b) const { return (a != b) ? 1.0 : 0.0; }
double Approx::eval(double a, double b) const { return approx_equal(a, b); }
double Less::eval(double a, double b) const { return (a < b) ? 1.0 : 0.0; }
double LessEqual::eval(double a, double b) const { return (a <= b) ? 1.0 : 0.0; }
double Greater::eval(double a, double b) const { return (a > b) ? 1.0 : 0.0; }
double GreaterEqual::eval(double a, double b) const { return (a >= b) ? 1.0 : 0.0; }
double And::eval(double a, double b) const { return ((a != 0.0) && (b != 0.0)) ? 1.0 : 0.0; }
double Or::eval(double a, double b) const { return ((a != 0.0) || (b != 0.0)) ? 1.0 : 0.0; }
double Cos::eval(double a) const { return std::cos(a); }
double Sin::eval(double a) const { return std::sin(a); }
double Tan::eval(double a) const { return std::tan(a); }
double Cosh::eval(double a) const { return std::cosh(a); }
double Sinh::eval(double a) const { return std::sinh(a); }
double Tanh::eval(double a) const { return std::tanh(a); }
double Acos::eval(double a) const { return std::acos(a); }
double Asin::eval(double a) const { return std::asin(a); }
double Atan::eval(double a) const { return std::atan(a); }
double Exp::eval(double a) const { return std::exp(a); }
double Log10::eval(double a) const { return std::log10(a); }
double Log::eval(double a) const { return std::log(a); }
double Sqrt::eval(double a) const { return std::sqrt(a); }
double Ceil::eval(double a) const { return std::ceil(a); }
double Fabs::eval(double a) const { return std::fabs(a); }
double Floor::eval(double a) const { return std::floor(a); }
double Atan2::eval(double a, double b) const { return std::atan2(a, b); }
double Ldexp::eval(double a, double b) const { return std::ldexp(a, b); }
double Fmod::eval(double a, double b) const { return std::fmod(a, b); }
double Min::eval(double a, double b) const { return std::min(a, b); }
double Max::eval(double a, double b) const { return std::max(a, b); }
double IsNan::eval(double a) const { return std::isnan(a) ? 1.0 : 0.0; }
double Relu::eval(double a) const { return std::max(a, 0.0); }
double Sigmoid::eval(double a) const { return 1.0 / (1.0 + std::exp(-1.0 * a)); }
} // namespace vespalib::eval::operation

} // namespace vespalib::eval
} // namespace vespalib
