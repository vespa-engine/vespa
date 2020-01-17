// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace document {

// Sets the value of a variable for the duration of this object's lifetime.
// The original value is restored when this object is destroyed.
template <typename T>
class VarScope {
    T &_ptr;
    T _old;

public:
    VarScope (T &ptr, T new_val) :
        _ptr(ptr),
        _old(_ptr) {
        _ptr = new_val;
    }
    ~VarScope() { _ptr = _old; }
};

template <typename T, typename Input>
T readValue(Input &input) {
    T val;
    input >> val;
    return val;
}

template <typename Input>
uint32_t getInt1_4Bytes(Input &input) {
    char first_byte = *input.peek();
    if (!(first_byte & 0x80)) {
        return readValue<uint8_t>(input);
    } else {
        return readValue<uint32_t>(input) & 0x7fffffff;
    }
}

template <typename Input>
uint32_t getInt1_2_4Bytes(Input &input) {
    char first_byte = *input.peek();
    if (!(first_byte & 0x80)) {
        return readValue<uint8_t>(input);
    } else if (!(first_byte & 0x40)) {
        return readValue<uint16_t>(input) & 0x3fff;
    } else {
        return readValue<uint32_t>(input) & 0x3fffffff;
    }
}

template <typename Input>
uint64_t getInt2_4_8Bytes(Input &input) {
    char first_byte = *input.peek();
    if (!(first_byte & 0x80)) {
        return readValue<uint16_t>(input);
    } else if (!(first_byte & 0x40)) {
        return readValue<uint32_t>(input) & 0x3fffffff;
    } else {
        return readValue<uint64_t>(input) & 0x3fffffffffffffff;
    }
}

template <typename Output>
void putInt1_4Bytes(Output &out, uint32_t val) {
    if (val < 0x80) {
        out << static_cast<uint8_t>(val);
    } else {
        out << (val | 0x80000000);
    }
}

template <typename Output>
void putInt1_2_4Bytes(Output &out, uint32_t val) {
    if (val < 0x80) {
        out << static_cast<uint8_t>(val);
    } else if (val < 0x4000) {
        out << static_cast<uint16_t>(val | 0x8000);
    } else {
        out << (val | 0xc0000000);
    }
}

template <typename Output>
void putInt1_2_4BytesAs4(Output &out, uint32_t val) {
    out << (val | 0xc0000000);
}

template <typename Output>
void putInt2_4_8Bytes(Output &out, uint64_t val) {
    if (val < 0x8000) {
        out << static_cast<uint16_t>(val);
    } else if (val < 0x40000000) {
        out << static_cast<uint32_t>(val | 0x80000000);
    } else {
        out << (val | 0xc000000000000000);
    }
}

}  // namespace document

