// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "basic_nodes.h"
#include "tensor_nodes.h"
#include "operator_nodes.h"
#include "call_nodes.h"

namespace vespalib {
namespace eval {

/**
 * Interface implemented by Node visitors to resolve the actual type
 * of an abstract Node. This is typically used when directly
 * evaluating an AST, when creating a binary compile cache key or when
 * compiling an AST to machine code using LLVM.
 **/
struct NodeVisitor {

    // basic nodes
    virtual void visit(const nodes::Number       &) = 0;
    virtual void visit(const nodes::Symbol       &) = 0;
    virtual void visit(const nodes::String       &) = 0;
    virtual void visit(const nodes::Array        &) = 0;
    virtual void visit(const nodes::Neg          &) = 0;
    virtual void visit(const nodes::Not          &) = 0;
    virtual void visit(const nodes::If           &) = 0;
    virtual void visit(const nodes::Let          &) = 0;
    virtual void visit(const nodes::Error        &) = 0;

    // tensor nodes
    virtual void visit(const nodes::TensorSum    &) = 0;
    virtual void visit(const nodes::TensorMap    &) = 0;
    virtual void visit(const nodes::TensorJoin   &) = 0;
    virtual void visit(const nodes::TensorReduce &) = 0;
    virtual void visit(const nodes::TensorRename &) = 0;
    virtual void visit(const nodes::TensorLambda &) = 0;
    virtual void visit(const nodes::TensorConcat &) = 0;

    // operator nodes
    virtual void visit(const nodes::Add          &) = 0;
    virtual void visit(const nodes::Sub          &) = 0;
    virtual void visit(const nodes::Mul          &) = 0;
    virtual void visit(const nodes::Div          &) = 0;
    virtual void visit(const nodes::Pow          &) = 0;
    virtual void visit(const nodes::Equal        &) = 0;
    virtual void visit(const nodes::NotEqual     &) = 0;
    virtual void visit(const nodes::Approx       &) = 0;
    virtual void visit(const nodes::Less         &) = 0;
    virtual void visit(const nodes::LessEqual    &) = 0;
    virtual void visit(const nodes::Greater      &) = 0;
    virtual void visit(const nodes::GreaterEqual &) = 0;
    virtual void visit(const nodes::In           &) = 0;
    virtual void visit(const nodes::And          &) = 0;
    virtual void visit(const nodes::Or           &) = 0;

    // call nodes
    virtual void visit(const nodes::Cos          &) = 0;
    virtual void visit(const nodes::Sin          &) = 0;
    virtual void visit(const nodes::Tan          &) = 0;
    virtual void visit(const nodes::Cosh         &) = 0;
    virtual void visit(const nodes::Sinh         &) = 0;
    virtual void visit(const nodes::Tanh         &) = 0;
    virtual void visit(const nodes::Acos         &) = 0;
    virtual void visit(const nodes::Asin         &) = 0;
    virtual void visit(const nodes::Atan         &) = 0;
    virtual void visit(const nodes::Exp          &) = 0;
    virtual void visit(const nodes::Log10        &) = 0;
    virtual void visit(const nodes::Log          &) = 0;
    virtual void visit(const nodes::Sqrt         &) = 0;
    virtual void visit(const nodes::Ceil         &) = 0;
    virtual void visit(const nodes::Fabs         &) = 0;
    virtual void visit(const nodes::Floor        &) = 0;
    virtual void visit(const nodes::Atan2        &) = 0;
    virtual void visit(const nodes::Ldexp        &) = 0;
    virtual void visit(const nodes::Pow2         &) = 0;
    virtual void visit(const nodes::Fmod         &) = 0;
    virtual void visit(const nodes::Min          &) = 0;
    virtual void visit(const nodes::Max          &) = 0;
    virtual void visit(const nodes::IsNan        &) = 0;
    virtual void visit(const nodes::Relu         &) = 0;
    virtual void visit(const nodes::Sigmoid      &) = 0;

    virtual ~NodeVisitor() {}
};

/**
 * Node visitor helper class that can be subclassed to ignore handling
 * of all types not specifically handled.
 **/
struct EmptyNodeVisitor : NodeVisitor {
    virtual void visit(const nodes::Number       &) {}
    virtual void visit(const nodes::Symbol       &) {}
    virtual void visit(const nodes::String       &) {}
    virtual void visit(const nodes::Array        &) {}
    virtual void visit(const nodes::Neg          &) {}
    virtual void visit(const nodes::Not          &) {}
    virtual void visit(const nodes::If           &) {}
    virtual void visit(const nodes::Let          &) {}
    virtual void visit(const nodes::Error        &) {}
    virtual void visit(const nodes::TensorSum    &) {}
    virtual void visit(const nodes::TensorMap    &) {}
    virtual void visit(const nodes::TensorJoin   &) {}
    virtual void visit(const nodes::TensorReduce &) {}
    virtual void visit(const nodes::TensorRename &) {}
    virtual void visit(const nodes::TensorLambda &) {}
    virtual void visit(const nodes::TensorConcat &) {}
    virtual void visit(const nodes::Add          &) {}
    virtual void visit(const nodes::Sub          &) {}
    virtual void visit(const nodes::Mul          &) {}
    virtual void visit(const nodes::Div          &) {}
    virtual void visit(const nodes::Pow          &) {}
    virtual void visit(const nodes::Equal        &) {}
    virtual void visit(const nodes::NotEqual     &) {}
    virtual void visit(const nodes::Approx       &) {}
    virtual void visit(const nodes::Less         &) {}
    virtual void visit(const nodes::LessEqual    &) {}
    virtual void visit(const nodes::Greater      &) {}
    virtual void visit(const nodes::GreaterEqual &) {}
    virtual void visit(const nodes::In           &) {}
    virtual void visit(const nodes::And          &) {}
    virtual void visit(const nodes::Or           &) {}
    virtual void visit(const nodes::Cos          &) {}
    virtual void visit(const nodes::Sin          &) {}
    virtual void visit(const nodes::Tan          &) {}
    virtual void visit(const nodes::Cosh         &) {}
    virtual void visit(const nodes::Sinh         &) {}
    virtual void visit(const nodes::Tanh         &) {}
    virtual void visit(const nodes::Acos         &) {}
    virtual void visit(const nodes::Asin         &) {}
    virtual void visit(const nodes::Atan         &) {}
    virtual void visit(const nodes::Exp          &) {}
    virtual void visit(const nodes::Log10        &) {}
    virtual void visit(const nodes::Log          &) {}
    virtual void visit(const nodes::Sqrt         &) {}
    virtual void visit(const nodes::Ceil         &) {}
    virtual void visit(const nodes::Fabs         &) {}
    virtual void visit(const nodes::Floor        &) {}
    virtual void visit(const nodes::Atan2        &) {}
    virtual void visit(const nodes::Ldexp        &) {}
    virtual void visit(const nodes::Pow2         &) {}
    virtual void visit(const nodes::Fmod         &) {}
    virtual void visit(const nodes::Min          &) {}
    virtual void visit(const nodes::Max          &) {}
    virtual void visit(const nodes::IsNan        &) {}
    virtual void visit(const nodes::Relu         &) {}
    virtual void visit(const nodes::Sigmoid      &) {}
};

} // namespace vespalib::eval
} // namespace vespalib
