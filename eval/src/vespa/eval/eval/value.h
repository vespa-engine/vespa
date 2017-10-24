// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>
#include <memory>
#include <vespa/vespalib/util/stash.h>
#include "tensor.h"
#include "value_type.h"

namespace vespalib {
namespace eval {

class Tensor;

constexpr double error_value = 31212.0;

struct UnaryOperation;
struct BinaryOperation;

/**
 * An abstract Value. Calculation using abstract values should be done
 * using the perform function on the appropriate Operation.
 **/
struct Value {
    typedef std::unique_ptr<Value> UP;
    typedef std::reference_wrapper<const Value> CREF;
    virtual bool is_error() const { return false; }
    virtual bool is_double() const { return false; }
    virtual bool is_tensor() const { return false; }
    virtual double as_double() const { return 0.0; }
    virtual bool as_bool() const { return false; }
    virtual const Tensor *as_tensor() const { return nullptr; }
    virtual bool equal(const Value &rhs) const = 0;
    virtual const Value &apply(const UnaryOperation &op, Stash &stash) const;
    virtual const Value &apply(const BinaryOperation &op, const Value &rhs, Stash &stash) const;
    virtual ValueType type() const = 0;
    virtual ~Value() {}
};

struct ErrorValue : public Value {
    bool is_error() const override { return true; }
    double as_double() const override { return error_value; }
    bool equal(const Value &) const override { return false; }
    ValueType type() const override { return ValueType::error_type(); }
};

class DoubleValue : public Value
{
private:
    double _value;
public:
    DoubleValue(double value) : _value(value) {}
    bool is_double() const override { return true; }
    double as_double() const override { return _value; }
    bool as_bool() const override { return (_value != 0.0); }
    bool equal(const Value &rhs) const override {
        return (rhs.is_double() && (_value == rhs.as_double()));
    }
    ValueType type() const override { return ValueType::double_type(); }
};

class TensorValue : public Value
{
private:
    const Tensor *_tensor;
    std::unique_ptr<Tensor> _stored;
public:
    TensorValue(const Tensor &value) : _tensor(&value), _stored() {}
    TensorValue(std::unique_ptr<Tensor> value) : _tensor(value.get()), _stored(std::move(value)) {}
    bool is_tensor() const override { return true; }
    const Tensor *as_tensor() const override { return _tensor; }
    bool equal(const Value &rhs) const override;
    const Value &apply(const UnaryOperation &op, Stash &stash) const override;
    const Value &apply(const BinaryOperation &op, const Value &rhs, Stash &stash) const override;
    ValueType type() const override;
};

} // namespace vespalib::eval
} // namespace vespalib

VESPA_CAN_SKIP_DESTRUCTION(::vespalib::eval::DoubleValue);
