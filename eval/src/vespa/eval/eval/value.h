// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value_type.h"
#include <vespa/vespalib/util/traits.h>
#include <memory>

namespace vespalib::eval {

class Tensor;

/**
 * An abstract Value.
 **/
struct Value {
    typedef std::unique_ptr<Value> UP;
    typedef std::reference_wrapper<const Value> CREF;
    virtual bool is_double() const { return false; }
    virtual bool is_tensor() const { return false; }
    virtual double as_double() const { return 0.0; }
    bool as_bool() const { return (as_double() != 0.0); }
    virtual const Tensor *as_tensor() const { return nullptr; }
    virtual const ValueType &type() const = 0;
    virtual ~Value() {}
};

class DoubleValue : public Value
{
private:
    double _value;
    static ValueType _type;
public:
    DoubleValue(double value) : _value(value) {}
    bool is_double() const override { return true; }
    double as_double() const override { return _value; }
    const ValueType &type() const override { return _type; }
    static const ValueType &double_type() { return _type; }
};

}

VESPA_CAN_SKIP_DESTRUCTION(::vespalib::eval::DoubleValue);
