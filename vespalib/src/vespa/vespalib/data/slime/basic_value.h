// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "value.h"
#include "memory.h"
#include <vespa/vespalib/util/traits.h>
#include <vespa/vespalib/util/stash.h>

namespace vespalib {
namespace slime {

/**
 * Classes representing a single basic value.
 **/
class BasicBoolValue : public Value {
    bool _value;
public:
    BasicBoolValue(bool bit) : _value(bit) {}
    virtual bool asBool() const { return _value; }
    virtual Type type() const { return BOOL::instance; }
};

class BasicLongValue : public Value {
    int64_t _value;
public:
    BasicLongValue(int64_t l) : _value(l) {}
    virtual int64_t asLong() const { return _value; }
    virtual double asDouble() const { return _value; }
    virtual Type type() const { return LONG::instance; }
};

class BasicDoubleValue : public Value {
    double _value;
public:
    BasicDoubleValue(double d) : _value(d) {}
    virtual double asDouble() const { return _value; }
    virtual int64_t asLong() const { return _value; }
    virtual Type type() const { return DOUBLE::instance; }
};

class BasicStringValue : public Value {
    Memory _value;
public:
    BasicStringValue(Memory str, Stash & stash);
    BasicStringValue(const BasicStringValue &) = delete;
    BasicStringValue & operator = (const BasicStringValue &) = delete;
    virtual Memory asString() const { return _value; }
    virtual Type type() const { return STRING::instance; }
};

class BasicDataValue : public Value {
    Memory _value;
public:
    BasicDataValue(Memory data, Stash & stash);
    BasicDataValue(const BasicDataValue &) = delete;
    BasicDataValue & operator = (const BasicDataValue &) = delete;
    virtual Memory asData() const { return _value; }
    virtual Type type() const { return DATA::instance; }
};

} // namespace vespalib::slime
} // namespace vespalib

VESPA_CAN_SKIP_DESTRUCTION(vespalib::slime::BasicBoolValue);
VESPA_CAN_SKIP_DESTRUCTION(vespalib::slime::BasicLongValue);
VESPA_CAN_SKIP_DESTRUCTION(vespalib::slime::BasicDoubleValue);
VESPA_CAN_SKIP_DESTRUCTION(vespalib::slime::BasicStringValue);
VESPA_CAN_SKIP_DESTRUCTION(vespalib::slime::BasicDataValue);
