// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <stdint.h>

namespace vespalib {
namespace slime {

/**
 * A Symbol may be used to look up a field within an OBJECT.
 **/
class Symbol
{
private:
    static const uint32_t UNDEFINED = (uint32_t)-1;

    uint32_t _value;

public:
    Symbol() : _value(UNDEFINED) {}
    Symbol(uint32_t v) : _value(v) {}
    bool undefined() const { return (_value == UNDEFINED); }
    uint32_t getValue() const { return _value; }
    bool operator<(const Symbol &rhs) const noexcept { return (_value < rhs._value); }
    bool operator==(const Symbol &rhs) const noexcept { return (_value == rhs._value); }
};

} // namespace vespalib::slime
} // namespace vespalib

