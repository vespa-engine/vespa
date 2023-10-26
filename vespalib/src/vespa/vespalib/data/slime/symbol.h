// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace vespalib::slime {

/**
 * A Symbol may be used to look up a field within an OBJECT.
 **/
class Symbol
{
private:
    static constexpr uint32_t UNDEFINED = (uint32_t)-1;

    uint32_t _value;

public:
    Symbol() noexcept : _value(UNDEFINED) {}
    Symbol(uint32_t v) noexcept : _value(v) {}
    bool undefined() const noexcept { return (_value == UNDEFINED); }
    uint32_t getValue() const noexcept { return _value; }
    bool operator<(const Symbol &rhs) const noexcept { return (_value < rhs._value); }
    bool operator==(const Symbol &rhs) const noexcept { return (_value == rhs._value); }
};

} // namespace vespalib::slime
