// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "operation.h"

namespace vespalib {
namespace eval {

/**
 * Interface implemented by Operation visitors to resolve the actual
 * type of an abstract Operation.
 **/
struct OperationVisitor {
    virtual void visit(const operation::Neg          &) = 0;
    virtual void visit(const operation::Not          &) = 0;
    virtual void visit(const operation::Add          &) = 0;
    virtual void visit(const operation::Sub          &) = 0;
    virtual void visit(const operation::Mul          &) = 0;
    virtual void visit(const operation::Div          &) = 0;
    virtual void visit(const operation::Pow          &) = 0;
    virtual void visit(const operation::Equal        &) = 0;
    virtual void visit(const operation::NotEqual     &) = 0;
    virtual void visit(const operation::Approx       &) = 0;
    virtual void visit(const operation::Less         &) = 0;
    virtual void visit(const operation::LessEqual    &) = 0;
    virtual void visit(const operation::Greater      &) = 0;
    virtual void visit(const operation::GreaterEqual &) = 0;
    virtual void visit(const operation::And          &) = 0;
    virtual void visit(const operation::Or           &) = 0;
    virtual void visit(const operation::Cos          &) = 0;
    virtual void visit(const operation::Sin          &) = 0;
    virtual void visit(const operation::Tan          &) = 0;
    virtual void visit(const operation::Cosh         &) = 0;
    virtual void visit(const operation::Sinh         &) = 0;
    virtual void visit(const operation::Tanh         &) = 0;
    virtual void visit(const operation::Acos         &) = 0;
    virtual void visit(const operation::Asin         &) = 0;
    virtual void visit(const operation::Atan         &) = 0;
    virtual void visit(const operation::Exp          &) = 0;
    virtual void visit(const operation::Log10        &) = 0;
    virtual void visit(const operation::Log          &) = 0;
    virtual void visit(const operation::Sqrt         &) = 0;
    virtual void visit(const operation::Ceil         &) = 0;
    virtual void visit(const operation::Fabs         &) = 0;
    virtual void visit(const operation::Floor        &) = 0;
    virtual void visit(const operation::Atan2        &) = 0;
    virtual void visit(const operation::Ldexp        &) = 0;
    virtual void visit(const operation::Fmod         &) = 0;
    virtual void visit(const operation::Min          &) = 0;
    virtual void visit(const operation::Max          &) = 0;
    virtual void visit(const operation::IsNan        &) = 0;
    virtual void visit(const operation::Relu         &) = 0;
    virtual void visit(const operation::Sigmoid      &) = 0;
    virtual void visit(const CustomUnaryOperation    &) = 0;
    virtual ~OperationVisitor() {}
};

/**
 * Operation visitor helper class that can be subclassed to implement
 * common handling of all types not specifically handled.
 **/
struct DefaultOperationVisitor : OperationVisitor {
    virtual void visitDefault(const Operation &) = 0;
    virtual void visit(const operation::Neg          &op) override { visitDefault(op); }
    virtual void visit(const operation::Not          &op) override { visitDefault(op); }
    virtual void visit(const operation::Add          &op) override { visitDefault(op); }
    virtual void visit(const operation::Sub          &op) override { visitDefault(op); }
    virtual void visit(const operation::Mul          &op) override { visitDefault(op); }
    virtual void visit(const operation::Div          &op) override { visitDefault(op); }
    virtual void visit(const operation::Pow          &op) override { visitDefault(op); }
    virtual void visit(const operation::Equal        &op) override { visitDefault(op); }
    virtual void visit(const operation::NotEqual     &op) override { visitDefault(op); }
    virtual void visit(const operation::Approx       &op) override { visitDefault(op); }
    virtual void visit(const operation::Less         &op) override { visitDefault(op); }
    virtual void visit(const operation::LessEqual    &op) override { visitDefault(op); }
    virtual void visit(const operation::Greater      &op) override { visitDefault(op); }
    virtual void visit(const operation::GreaterEqual &op) override { visitDefault(op); }
    virtual void visit(const operation::And          &op) override { visitDefault(op); }
    virtual void visit(const operation::Or           &op) override { visitDefault(op); }
    virtual void visit(const operation::Cos          &op) override { visitDefault(op); }
    virtual void visit(const operation::Sin          &op) override { visitDefault(op); }
    virtual void visit(const operation::Tan          &op) override { visitDefault(op); }
    virtual void visit(const operation::Cosh         &op) override { visitDefault(op); }
    virtual void visit(const operation::Sinh         &op) override { visitDefault(op); }
    virtual void visit(const operation::Tanh         &op) override { visitDefault(op); }
    virtual void visit(const operation::Acos         &op) override { visitDefault(op); }
    virtual void visit(const operation::Asin         &op) override { visitDefault(op); }
    virtual void visit(const operation::Atan         &op) override { visitDefault(op); }
    virtual void visit(const operation::Exp          &op) override { visitDefault(op); }
    virtual void visit(const operation::Log10        &op) override { visitDefault(op); }
    virtual void visit(const operation::Log          &op) override { visitDefault(op); }
    virtual void visit(const operation::Sqrt         &op) override { visitDefault(op); }
    virtual void visit(const operation::Ceil         &op) override { visitDefault(op); }
    virtual void visit(const operation::Fabs         &op) override { visitDefault(op); }
    virtual void visit(const operation::Floor        &op) override { visitDefault(op); }
    virtual void visit(const operation::Atan2        &op) override { visitDefault(op); }
    virtual void visit(const operation::Ldexp        &op) override { visitDefault(op); }
    virtual void visit(const operation::Fmod         &op) override { visitDefault(op); }
    virtual void visit(const operation::Min          &op) override { visitDefault(op); }
    virtual void visit(const operation::Max          &op) override { visitDefault(op); }
    virtual void visit(const operation::IsNan        &op) override { visitDefault(op); }
    virtual void visit(const operation::Relu         &op) override { visitDefault(op); }
    virtual void visit(const operation::Sigmoid      &op) override { visitDefault(op); }
    virtual void visit(const CustomUnaryOperation    &op) override { visitDefault(op); }
};

} // namespace vespalib::eval
} // namespace vespalib
