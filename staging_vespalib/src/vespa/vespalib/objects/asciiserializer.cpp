// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/objects/asciiserializer.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib {

AsciiSerializer &AsciiSerializer::put(const IFieldBase &, bool value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(const IFieldBase &, uint8_t value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(const IFieldBase &, uint16_t value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(const IFieldBase &, uint32_t value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(const IFieldBase &, uint64_t value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(const IFieldBase &, float value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(const IFieldBase &, double value) {
    _stream << value;
    return *this;
}

AsciiSerializer &AsciiSerializer::put(const IFieldBase &, const stringref & value) {
    _stream << value;
    return *this;
}

}  // namespace vespalib
