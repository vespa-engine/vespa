// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_cache/constant_value.h>

namespace proton {
namespace matching {

/**
 * Class representing an error constant value.
 * Typically used to indicate that a named constant value does not exists.
 */
class ErrorConstantValue : public vespalib::eval::ConstantValue {
private:
    vespalib::eval::ErrorValue _value;
    vespalib::eval::ValueType _type;
public:
    ErrorConstantValue() : _value(), _type(vespalib::eval::ValueType::error_type()) {}
    virtual const vespalib::eval::Value &value() const override { return _value; }
    virtual const vespalib::eval::ValueType &type() const override { return _type; }
};

}
}
