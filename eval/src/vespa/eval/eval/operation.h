// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include <cmath>
#include <vespa/vespalib/util/approx.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib {
namespace eval {

struct OperationVisitor;

/**
 * An Operation represents the action taken based on what is described
 * by an Operator or a Call AST node. All operations have underlying
 * numeric meaning (that can be overridden for complex value
 * types). They no longer have any textual counterpart and are only
 * separated by the number of values they operate on.
 **/
struct Operation {
    using op1_fun_t = double (*)(double);
    using op2_fun_t = double (*)(double, double);
    virtual void accept(OperationVisitor &visitor) const = 0;
    virtual ~Operation() {}
};

/**
 * Simple typecasting utility.
 */
template <typename T>
const T *as(const Operation &op) { return dynamic_cast<const T *>(&op); }

//-----------------------------------------------------------------------------

/**
 * An Operation performing a calculation based on a single input
 * value.
 **/
struct UnaryOperation : Operation {
    const Value &perform(const Value &a, Stash &stash) const;
    virtual double eval(double a) const = 0;
    virtual op1_fun_t get_f() const = 0;
};

/**
 * An Operation performing a calculation based on two input values.
 **/
struct BinaryOperation : Operation {
    const Value &perform(const Value &a, const Value &b, Stash &stash) const;
    virtual double eval(double a, double b) const = 0;
    virtual std::unique_ptr<BinaryOperation> clone() const = 0;
    virtual op2_fun_t get_f() const = 0;
};

//-----------------------------------------------------------------------------

/**
 * Utility class used to adapt stateless function pointers to stateful
 * functors by using thread-local bindings.
 **/
class UnaryOperationProxy {
private:
    static __thread const UnaryOperation *_ctx;
    static double eval_proxy(double a);
    const UnaryOperation *_my_ctx;
    const UnaryOperation *_old_ctx;
public:
    using fun_t = double (*)(double);
    UnaryOperationProxy(const UnaryOperation &op);
    operator fun_t() const { return eval_proxy; }
    ~UnaryOperationProxy();
};

/**
 * Utility class used to adapt stateless function pointers to stateful
 * functors by using thread-local bindings.
 **/
class BinaryOperationProxy {
private:
    static __thread const BinaryOperation *_ctx;
    static double eval_proxy(double a, double b);
    const BinaryOperation *_my_ctx;
    const BinaryOperation *_old_ctx;
public:
    using fun_t = double (*)(double, double);
    BinaryOperationProxy(const BinaryOperation &op);
    operator fun_t() const { return eval_proxy; }
    ~BinaryOperationProxy();
};

//-----------------------------------------------------------------------------

template <typename T>
struct Op1 : UnaryOperation {
    virtual void accept(OperationVisitor &visitor) const override;
    virtual double eval(double a) const override;
    virtual op1_fun_t get_f() const override;
};

template <typename T>
struct Op2 : BinaryOperation {
    virtual void accept(OperationVisitor &visitor) const override;
    virtual std::unique_ptr<BinaryOperation> clone() const override;
    virtual double eval(double a, double b) const final override;
    virtual op2_fun_t get_f() const override;
};

//-----------------------------------------------------------------------------

/**
 * A non-trivial custom unary operation. Typically used for closures
 * and lambdas.
 **/
struct CustomUnaryOperation : Op1<CustomUnaryOperation> {
    static double f(double) { return error_value; }
};

//-----------------------------------------------------------------------------

/**
 * This class binds the first parameter of a binary operation to a
 * numeric value, acting as a custom unary operation.
 **/
class BindLeft : public CustomUnaryOperation
{
private:
    const BinaryOperation &_op;
    double _a;
public:
    BindLeft(const BinaryOperation &op, double a) : _op(op), _a(a) {}
    double eval(double b) const override { return _op.eval(_a, b); }
};

/**
 * This class binds the second parameter of a binary operation to a
 * numeric value, acting as a custom unary operation.
 **/
class BindRight : public CustomUnaryOperation
{
private:
    const BinaryOperation &_op;
    double _b;
public:
    BindRight(const BinaryOperation &op, double b) : _op(op), _b(b) {}
    double eval(double a) const override { return _op.eval(a, _b); }
};

//-----------------------------------------------------------------------------

namespace operation {
struct Neg : Op1<Neg> { static double f(double a); };
struct Not : Op1<Not> { static double f(double a); };
struct Add : Op2<Add> { static double f(double a, double b); };
struct Sub : Op2<Sub> { static double f(double a, double b); };
struct Mul : Op2<Mul> { static double f(double a, double b); };
struct Div : Op2<Div> { static double f(double a, double b); };
struct Mod : Op2<Mod> { static double f(double a, double b); };
struct Pow : Op2<Pow> { static double f(double a, double b); };
struct Equal : Op2<Equal> { static double f(double a, double b); };
struct NotEqual : Op2<NotEqual> { static double f(double a, double b); };
struct Approx : Op2<Approx> { static double f(double a, double b); };
struct Less : Op2<Less> { static double f(double a, double b); };
struct LessEqual : Op2<LessEqual> { static double f(double a, double b); };
struct Greater : Op2<Greater> { static double f(double a, double b); };
struct GreaterEqual : Op2<GreaterEqual> { static double f(double a, double b); };
struct And : Op2<And> { static double f(double a, double b); };
struct Or : Op2<Or> { static double f(double a, double b); };
struct Cos : Op1<Cos> { static double f(double a); };
struct Sin : Op1<Sin> { static double f(double a); };
struct Tan : Op1<Tan> { static double f(double a); };
struct Cosh : Op1<Cosh> { static double f(double a); };
struct Sinh : Op1<Sinh> { static double f(double a); };
struct Tanh : Op1<Tanh> { static double f(double a); };
struct Acos : Op1<Acos> { static double f(double a); };
struct Asin : Op1<Asin> { static double f(double a); };
struct Atan : Op1<Atan> { static double f(double a); };
struct Exp : Op1<Exp> { static double f(double a); };
struct Log10 : Op1<Log10> { static double f(double a); };
struct Log : Op1<Log> { static double f(double a); };
struct Sqrt : Op1<Sqrt> { static double f(double a); };
struct Ceil : Op1<Ceil> { static double f(double a); };
struct Fabs : Op1<Fabs> { static double f(double a); };
struct Floor : Op1<Floor> { static double f(double a); };
struct Atan2 : Op2<Atan2> { static double f(double a, double b); };
struct Ldexp : Op2<Ldexp> { static double f(double a, double b); };
struct Min : Op2<Min> { static double f(double a, double b); };
struct Max : Op2<Max> { static double f(double a, double b); };
struct IsNan : Op1<IsNan> { static double f(double a); };
struct Relu : Op1<Relu> { static double f(double a); };
struct Sigmoid : Op1<Sigmoid> { static double f(double a); };
} // namespace vespalib::eval::operation

} // namespace vespalib::eval
} // namespace vespalib
