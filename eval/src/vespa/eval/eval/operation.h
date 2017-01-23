// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
};

/**
 * An Operation performing a calculation based on two input values.
 **/
struct BinaryOperation : Operation {
    const Value &perform(const Value &a, const Value &b, Stash &stash) const;
    virtual double eval(double a, double b) const = 0;
    virtual std::unique_ptr<BinaryOperation> clone() const = 0;
};

//-----------------------------------------------------------------------------

template <typename T>
struct Op1 : UnaryOperation {
    virtual void accept(OperationVisitor &visitor) const override;
};

template <typename T>
struct Op2 : BinaryOperation {
    virtual void accept(OperationVisitor &visitor) const override;
    virtual std::unique_ptr<BinaryOperation> clone() const override;
};

//-----------------------------------------------------------------------------

/**
 * A non-trivial custom unary operation. Typically used for closures
 * and lambdas.
 **/
struct CustomUnaryOperation : Op1<CustomUnaryOperation> {};

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
struct Neg : Op1<Neg> { double eval(double a) const override; };
struct Not : Op1<Not> { double eval(double a) const override; };
struct Add : Op2<Add> { double eval(double a, double b) const override; };
struct Sub : Op2<Sub> { double eval(double a, double b) const override; };
struct Mul : Op2<Mul> { double eval(double a, double b) const override; };
struct Div : Op2<Div> { double eval(double a, double b) const override; };
struct Pow : Op2<Pow> { double eval(double a, double b) const override; };
struct Equal : Op2<Equal> { double eval(double a, double b) const override; };
struct NotEqual : Op2<NotEqual> { double eval(double a, double b) const override; };
struct Approx : Op2<Approx> { double eval(double a, double b) const override; };
struct Less : Op2<Less> { double eval(double a, double b) const override; };
struct LessEqual : Op2<LessEqual> { double eval(double a, double b) const override; };
struct Greater : Op2<Greater> { double eval(double a, double b) const override; };
struct GreaterEqual : Op2<GreaterEqual> { double eval(double a, double b) const override; };
struct And : Op2<And> { double eval(double a, double b) const override; };
struct Or : Op2<Or> { double eval(double a, double b) const override; };
struct Cos : Op1<Cos> { double eval(double a) const override; };
struct Sin : Op1<Sin> { double eval(double a) const override; };
struct Tan : Op1<Tan> { double eval(double a) const override; };
struct Cosh : Op1<Cosh> { double eval(double a) const override; };
struct Sinh : Op1<Sinh> { double eval(double a) const override; };
struct Tanh : Op1<Tanh> { double eval(double a) const override; };
struct Acos : Op1<Acos> { double eval(double a) const override; };
struct Asin : Op1<Asin> { double eval(double a) const override; };
struct Atan : Op1<Atan> { double eval(double a) const override; };
struct Exp : Op1<Exp> { double eval(double a) const override; };
struct Log10 : Op1<Log10> { double eval(double a) const override; };
struct Log : Op1<Log> { double eval(double a) const override; };
struct Sqrt : Op1<Sqrt> { double eval(double a) const override; };
struct Ceil : Op1<Ceil> { double eval(double a) const override; };
struct Fabs : Op1<Fabs> { double eval(double a) const override; };
struct Floor : Op1<Floor> { double eval(double a) const override; };
struct Atan2 : Op2<Atan2> { double eval(double a, double b) const override; };
struct Ldexp : Op2<Ldexp> { double eval(double a, double b) const override; };
struct Fmod : Op2<Fmod> { double eval(double a, double b) const override; };
struct Min : Op2<Min> { double eval(double a, double b) const override; };
struct Max : Op2<Max> { double eval(double a, double b) const override; };
struct IsNan : Op1<IsNan> { double eval(double a) const override; };
struct Relu : Op1<Relu> { double eval(double a) const override; };
struct Sigmoid : Op1<Sigmoid> { double eval(double a) const override; };
} // namespace vespalib::eval::operation

} // namespace vespalib::eval
} // namespace vespalib
