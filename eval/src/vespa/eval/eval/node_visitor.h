// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    virtual void visit(const nodes::Number         &) = 0;
    virtual void visit(const nodes::Symbol         &) = 0;
    virtual void visit(const nodes::String         &) = 0;
    virtual void visit(const nodes::In             &) = 0;
    virtual void visit(const nodes::Neg            &) = 0;
    virtual void visit(const nodes::Not            &) = 0;
    virtual void visit(const nodes::If             &) = 0;
    virtual void visit(const nodes::Error          &) = 0;

    // tensor nodes
    virtual void visit(const nodes::TensorMap      &) = 0;
    virtual void visit(const nodes::TensorJoin     &) = 0;
    virtual void visit(const nodes::TensorMerge    &) = 0;
    virtual void visit(const nodes::TensorReduce   &) = 0;
    virtual void visit(const nodes::TensorRename   &) = 0;
    virtual void visit(const nodes::TensorConcat   &) = 0;
    virtual void visit(const nodes::TensorCellCast &) = 0;
    virtual void visit(const nodes::TensorCreate   &) = 0;
    virtual void visit(const nodes::TensorLambda   &) = 0;
    virtual void visit(const nodes::TensorPeek     &) = 0;

    // operator nodes
    virtual void visit(const nodes::Add            &) = 0;
    virtual void visit(const nodes::Sub            &) = 0;
    virtual void visit(const nodes::Mul            &) = 0;
    virtual void visit(const nodes::Div            &) = 0;
    virtual void visit(const nodes::Mod            &) = 0;
    virtual void visit(const nodes::Pow            &) = 0;
    virtual void visit(const nodes::Equal          &) = 0;
    virtual void visit(const nodes::NotEqual       &) = 0;
    virtual void visit(const nodes::Approx         &) = 0;
    virtual void visit(const nodes::Less           &) = 0;
    virtual void visit(const nodes::LessEqual      &) = 0;
    virtual void visit(const nodes::Greater        &) = 0;
    virtual void visit(const nodes::GreaterEqual   &) = 0;
    virtual void visit(const nodes::And            &) = 0;
    virtual void visit(const nodes::Or             &) = 0;

    // call nodes
    virtual void visit(const nodes::Cos            &) = 0;
    virtual void visit(const nodes::Sin            &) = 0;
    virtual void visit(const nodes::Tan            &) = 0;
    virtual void visit(const nodes::Cosh           &) = 0;
    virtual void visit(const nodes::Sinh           &) = 0;
    virtual void visit(const nodes::Tanh           &) = 0;
    virtual void visit(const nodes::Acos           &) = 0;
    virtual void visit(const nodes::Asin           &) = 0;
    virtual void visit(const nodes::Atan           &) = 0;
    virtual void visit(const nodes::Exp            &) = 0;
    virtual void visit(const nodes::Log10          &) = 0;
    virtual void visit(const nodes::Log            &) = 0;
    virtual void visit(const nodes::Sqrt           &) = 0;
    virtual void visit(const nodes::Ceil           &) = 0;
    virtual void visit(const nodes::Fabs           &) = 0;
    virtual void visit(const nodes::Floor          &) = 0;
    virtual void visit(const nodes::Atan2          &) = 0;
    virtual void visit(const nodes::Ldexp          &) = 0;
    virtual void visit(const nodes::Pow2           &) = 0;
    virtual void visit(const nodes::Fmod           &) = 0;
    virtual void visit(const nodes::Min            &) = 0;
    virtual void visit(const nodes::Max            &) = 0;
    virtual void visit(const nodes::IsNan          &) = 0;
    virtual void visit(const nodes::Relu           &) = 0;
    virtual void visit(const nodes::Sigmoid        &) = 0;
    virtual void visit(const nodes::Elu            &) = 0;
    virtual void visit(const nodes::Erf            &) = 0;

    virtual ~NodeVisitor() {}
};

/**
 * Node visitor helper class that can be subclassed to ignore handling
 * of all types not specifically handled.
 **/
struct EmptyNodeVisitor : NodeVisitor {
    void visit(const nodes::Number         &) override {}
    void visit(const nodes::Symbol         &) override {}
    void visit(const nodes::String         &) override {}
    void visit(const nodes::In             &) override {}
    void visit(const nodes::Neg            &) override {}
    void visit(const nodes::Not            &) override {}
    void visit(const nodes::If             &) override {}
    void visit(const nodes::Error          &) override {}
    void visit(const nodes::TensorMap      &) override {}
    void visit(const nodes::TensorJoin     &) override {}
    void visit(const nodes::TensorMerge    &) override {}
    void visit(const nodes::TensorReduce   &) override {}
    void visit(const nodes::TensorRename   &) override {}
    void visit(const nodes::TensorConcat   &) override {}
    void visit(const nodes::TensorCellCast &) override {}
    void visit(const nodes::TensorCreate   &) override {}
    void visit(const nodes::TensorLambda   &) override {}
    void visit(const nodes::TensorPeek     &) override {}
    void visit(const nodes::Add            &) override {}
    void visit(const nodes::Sub            &) override {}
    void visit(const nodes::Mul            &) override {}
    void visit(const nodes::Div            &) override {}
    void visit(const nodes::Mod            &) override {}
    void visit(const nodes::Pow            &) override {}
    void visit(const nodes::Equal          &) override {}
    void visit(const nodes::NotEqual       &) override {}
    void visit(const nodes::Approx         &) override {}
    void visit(const nodes::Less           &) override {}
    void visit(const nodes::LessEqual      &) override {}
    void visit(const nodes::Greater        &) override {}
    void visit(const nodes::GreaterEqual   &) override {}
    void visit(const nodes::And            &) override {}
    void visit(const nodes::Or             &) override {}
    void visit(const nodes::Cos            &) override {}
    void visit(const nodes::Sin            &) override {}
    void visit(const nodes::Tan            &) override {}
    void visit(const nodes::Cosh           &) override {}
    void visit(const nodes::Sinh           &) override {}
    void visit(const nodes::Tanh           &) override {}
    void visit(const nodes::Acos           &) override {}
    void visit(const nodes::Asin           &) override {}
    void visit(const nodes::Atan           &) override {}
    void visit(const nodes::Exp            &) override {}
    void visit(const nodes::Log10          &) override {}
    void visit(const nodes::Log            &) override {}
    void visit(const nodes::Sqrt           &) override {}
    void visit(const nodes::Ceil           &) override {}
    void visit(const nodes::Fabs           &) override {}
    void visit(const nodes::Floor          &) override {}
    void visit(const nodes::Atan2          &) override {}
    void visit(const nodes::Ldexp          &) override {}
    void visit(const nodes::Pow2           &) override {}
    void visit(const nodes::Fmod           &) override {}
    void visit(const nodes::Min            &) override {}
    void visit(const nodes::Max            &) override {}
    void visit(const nodes::IsNan          &) override {}
    void visit(const nodes::Relu           &) override {}
    void visit(const nodes::Sigmoid        &) override {}
    void visit(const nodes::Elu            &) override {}
    void visit(const nodes::Erf            &) override {}
};

} // namespace vespalib::eval
} // namespace vespalib
