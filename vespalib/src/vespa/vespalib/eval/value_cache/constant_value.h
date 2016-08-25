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
