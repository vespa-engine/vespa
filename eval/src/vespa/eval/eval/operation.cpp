// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "operation.h"
#include "value.h"
#include "operation_visitor.h"
#include <cmath>
#include <assert.h>

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

__thread const UnaryOperation *UnaryOperationProxy::_ctx = nullptr;
double UnaryOperationProxy::eval_proxy(double a) { return _ctx->eval(a); }
UnaryOperationProxy::UnaryOperationProxy(const UnaryOperation &op)
    : _my_ctx(&op),
      _old_ctx(_ctx)
{
    _ctx = _my_ctx;
}
UnaryOperationProxy::~UnaryOperationProxy()
{
    assert(_ctx == _my_ctx);
    _ctx = _old_ctx;
}

__thread const BinaryOperation *BinaryOperationProxy::_ctx = nullptr;
double BinaryOperationProxy::eval_proxy(double a, double b) { return _ctx->eval(a, b); }
BinaryOperationProxy::BinaryOperationProxy(const BinaryOperation &op)
    : _my_ctx(&op),
      _old_ctx(_ctx)
{
    _ctx = _my_ctx;
}
BinaryOperationProxy::~BinaryOperationProxy()
{
    assert(_ctx == _my_ctx);
    _ctx = _old_ctx;
}

template <typename T> void Op1<T>::accept(OperationVisitor &visitor) const {
    visitor.visit(static_cast<const T&>(*this));
}

template <typename T> double Op1<T>::eval(double a) const {
    return T::f(a);
}

template <typename T> Operation::op1_fun_t Op1<T>::get_f() const {
    return T::f;
}

template <typename T> void Op2<T>::accept(OperationVisitor &visitor) const {
    visitor.visit(static_cast<const T&>(*this));
}

template <typename T> std::unique_ptr<BinaryOperation> Op2<T>::clone() const {
    return std::make_unique<T>();
}

template <typename T> double Op2<T>::eval(double a, double b) const {
    return T::f(a, b);
}

template <typename T> Operation::op2_fun_t Op2<T>::get_f() const {
    return T::f;
}

namespace operation {
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
} // namespace vespalib::eval::operation

template struct Op1<operation::Neg>;
template struct Op1<operation::Not>;
template struct Op2<operation::Add>;
template struct Op2<operation::Sub>;
template struct Op2<operation::Mul>;
template struct Op2<operation::Div>;
template struct Op2<operation::Mod>;
template struct Op2<operation::Pow>;
template struct Op2<operation::Equal>;
template struct Op2<operation::NotEqual>;
template struct Op2<operation::Approx>;
template struct Op2<operation::Less>;
template struct Op2<operation::LessEqual>;
template struct Op2<operation::Greater>;
template struct Op2<operation::GreaterEqual>;
template struct Op2<operation::And>;
template struct Op2<operation::Or>;
template struct Op1<operation::Cos>;
template struct Op1<operation::Sin>;
template struct Op1<operation::Tan>;
template struct Op1<operation::Cosh>;
template struct Op1<operation::Sinh>;
template struct Op1<operation::Tanh>;
template struct Op1<operation::Acos>;
template struct Op1<operation::Asin>;
template struct Op1<operation::Atan>;
template struct Op1<operation::Exp>;
template struct Op1<operation::Log10>;
template struct Op1<operation::Log>;
template struct Op1<operation::Sqrt>;
template struct Op1<operation::Ceil>;
template struct Op1<operation::Fabs>;
template struct Op1<operation::Floor>;
template struct Op2<operation::Atan2>;
template struct Op2<operation::Ldexp>;
template struct Op2<operation::Min>;
template struct Op2<operation::Max>;
template struct Op1<operation::IsNan>;
template struct Op1<operation::Relu>;
template struct Op1<operation::Sigmoid>;

} // namespace vespalib::eval
} // namespace vespalib
