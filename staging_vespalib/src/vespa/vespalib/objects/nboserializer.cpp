// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "nboserializer.h"
#include <vespa/vespalib/objects/nbostream.h>

namespace vespalib {

const char * NBOSerializer::peek() const {
    return _stream.peek();
}

NBOSerializer &NBOSerializer::put(bool value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(uint8_t value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(uint16_t value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(uint32_t value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(uint64_t value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(float value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(double value) {
    _stream << value;
    return *this;
}

NBOSerializer &NBOSerializer::put(stringref value) {
    _stream << value;
    return *this;
}


NBOSerializer &NBOSerializer::get(bool & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(uint8_t & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(uint16_t & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(uint32_t & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(uint64_t & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(double & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(float & value) {
    _stream >> value;
    return *this;
}

NBOSerializer &NBOSerializer::get(string & value) {
    _stream >> value;
    return *this;
}
}  // namespace vespalib
