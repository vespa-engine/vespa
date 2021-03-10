// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vespa/vespalib/objects/nbostream.h>

namespace vespalib::eval {

/**
 * Class holding an 8-bit integer which decays into float.
 **/
class Int8Float {
private:
    int8_t _bits;
public:
    constexpr Int8Float(float value) noexcept : _bits(value) {}
    Int8Float() noexcept = default;
    ~Int8Float() noexcept = default;
    constexpr Int8Float(const Int8Float &other) noexcept = default;
    constexpr Int8Float(Int8Float &&other) noexcept = default;
    constexpr Int8Float& operator=(const Int8Float &other) noexcept = default;
    constexpr Int8Float& operator=(Int8Float &&other) noexcept = default;
    constexpr Int8Float& operator=(float value) noexcept {
        _bits = value;
        return *this;
    }

    constexpr operator float () const noexcept { return _bits; }

    constexpr float to_float() const noexcept { return _bits; }
    constexpr void assign(float value) noexcept { _bits = value; }

    constexpr int8_t get_bits() const { return _bits; }
    constexpr void assign_bits(int8_t value) noexcept { _bits = value; }
};

inline nbostream & operator << (nbostream &stream, Int8Float v)   { return stream << v.get_bits(); }
inline nbostream & operator >> (nbostream &stream, Int8Float & v) {
    int8_t byte;
    stream >> byte;
    v.assign_bits(byte);
    return stream;
}

}
