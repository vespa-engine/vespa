// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/eval/eval/value.h>
#include <vespa/eval/eval/value_type.h>

namespace vespalib {
namespace eval {

/**
 * Abstract wrapper of a typed constant value. The lifetime of the
 * wrapper controls the lifetime of the underlying type and value as
 * well.
 **/
struct ConstantValue {
    virtual const ValueType &type() const = 0;
    virtual const Value &value() const = 0;
    using UP = std::unique_ptr<ConstantValue>;
    virtual ~ConstantValue() {}
};

class SimpleConstantValue : public ConstantValue {
private:
    const Value::UP _value;
public:
    SimpleConstantValue(Value::UP value) : _value(std::move(value)) {}
    const ValueType &type() const override { return _value->type(); }
    const Value &value() const override { return *_value; }
};

class BadConstantValue : public ConstantValue {
private:
    const ValueType _type;
public:
    BadConstantValue() : _type(ValueType::error_type()) {}
    const ValueType &type() const override { return _type; }
    const Value &value() const override { abort(); }
};

/**
 * An abstract factory of constant values. The typical use-case for
 * this will be to load constant values from file with a cache on top
 * to share constants among users.
 **/
struct ConstantValueFactory {
    virtual ConstantValue::UP create(const vespalib::string &path, const vespalib::string &type) const = 0;
    virtual ~ConstantValueFactory() {}
};

} // namespace vespalib::eval
} // namespace vespalib
