// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <memory>
#include <vespa/vespalib/eval/value.h>
#include <vespa/vespalib/eval/value_type.h>

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

/**
 * A simple implementation of a constant value that bundles together a
 * ValueType instance with a specific Value subclass instance.
 **/
template <typename VALUE>
struct SimpleConstantValue : ConstantValue {
    ValueType my_type;
    VALUE my_value;
    template <typename... Args>
    SimpleConstantValue(const ValueType &type_in, Args &&...args)
        : my_type(type_in), my_value(std::forward<Args>(args)...) {}
    const ValueType &type() const override { return my_type; }
    const Value &value() const override { return my_value; }
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
