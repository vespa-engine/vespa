// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "nboserializer.h"
#include <vespa/vespalib/objects/nbostream.h>

namespace vespalib {

const char * NBOSerializer::peek() const {
    return _stream.peek();
}

NBOSerializer &NBOSerializer::put(const IFieldBase &, bool value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(const IFieldBase &, uint8_t value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(const IFieldBase &, uint16_t value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(const IFieldBase &, uint32_t value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(const IFieldBase &, uint64_t value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(const IFieldBase &, float value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(const IFieldBase &, double value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(const IFieldBase &, const stringref & value) {
    _stream << value;
    return *this;
}


NBOSerializer &NBOSerializer::get(const IFieldBase &, bool & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(const IFieldBase &, uint8_t & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(const IFieldBase &, uint16_t & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(const IFieldBase &, uint32_t & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(const IFieldBase &, uint64_t & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(const IFieldBase &, double & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(const IFieldBase &, float & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(const IFieldBase &, string & value) {
    _stream >> value;
    return *this;
}
}  // namespace vespalib
