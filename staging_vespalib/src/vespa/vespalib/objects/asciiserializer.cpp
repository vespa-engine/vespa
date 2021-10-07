// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "asciiserializer.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib {

AsciiSerializer &AsciiSerializer::put(bool value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(uint8_t value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(uint16_t value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(uint32_t value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(uint64_t value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(float value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(double value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(stringref value) {
    _stream << value;
    return *this;
}

}  // namespace vespalib
