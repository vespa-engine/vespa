// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vector>
#include <vespa/vespalib/stllike/string.h>
#include "value_type.h"
#include "tensor_function.h"
#include "aggr.h"

namespace vespalib {

class Stash;

namespace eval {

class Value;
class Tensor;
class TensorSpec;
struct UnaryOperation;
struct BinaryOperation;

/**
 * Top-level API for a tensor implementation. All Tensor operations
 * are defined by the TensorEngine interface. The Tensor class itself
 * is used as a tagged transport mechanism. Each Tensor is connected
 * to a distinct engine which can be used to operate on it. When
 * operating on multiple tensors at the same time they all need to be
 * connected to the same engine. TensorEngines should only have a
 * single static instance per implementation.
 **/
struct TensorEngine
{
    using ValueType = eval::ValueType;
    using Tensor = eval::Tensor;
    using TensorSpec = eval::TensorSpec;
    using Value = eval::Value;
    using BinaryOperation = eval::BinaryOperation;
    using UnaryOperation = eval::UnaryOperation;
    using Aggr = eval::Aggr;

    virtual ValueType type_of(const Tensor &tensor) const = 0;
    virtual bool equal(const Tensor &a, const Tensor &b) const = 0;
    virtual vespalib::string to_string(const Tensor &tensor) const = 0;
    virtual TensorSpec to_spec(const Tensor &tensor) const = 0;

    virtual TensorFunction::UP compile(tensor_function::Node_UP expr) const { return std::move(expr); }

    virtual std::unique_ptr<Tensor> create(const TensorSpec &spec) const = 0;
    virtual const Value &reduce(const Tensor &tensor, const BinaryOperation &op, const std::vector<vespalib::string> &dimensions, Stash &stash) const = 0;
    virtual const Value &map(const UnaryOperation &op, const Tensor &a, Stash &stash) const = 0;
    virtual const Value &apply(const BinaryOperation &op, const Tensor &a, const Tensor &b, Stash &stash) const = 0;

    // havardpe: new API, WIP
    virtual const Value &map(const Value &a, const std::function<double(double)> &function, Stash &stash) const = 0;
    virtual const Value &join(const Value &a, const Value &b, const std::function<double(double,double)> &function, Stash &stash) const = 0;
    virtual const Value &reduce(const Value &a, Aggr aggr, const std::vector<vespalib::string> &dimensions, Stash &stash) const = 0;
    virtual const Value &concat(const Value &a, const Value &b, const vespalib::string &dimension, Stash &stash) const = 0;
    virtual const Value &rename(const Value &a, const std::vector<vespalib::string> &from, const std::vector<vespalib::string> &to, Stash &stash) const = 0;

    virtual ~TensorEngine() {}
};

} // namespace vespalib::eval
} // namespace vespalib
