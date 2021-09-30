// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "serializer.h"
#include "deserializer.h"

namespace vespalib {

class asciistream;

class AsciiSerializer : public Serializer {
public:
    AsciiSerializer(asciistream &stream) : _stream(stream) { }
    AsciiSerializer &put(bool value) override;
    AsciiSerializer &put(uint8_t value) override;
    AsciiSerializer &put(uint16_t value) override;
    AsciiSerializer &put(uint32_t value) override;
    AsciiSerializer &put(uint64_t value) override;
    AsciiSerializer &put(float value) override;
    AsciiSerializer &put(double value) override;
    AsciiSerializer &put(stringref val) override;

    const asciistream &getStream() const { return _stream; }
    asciistream &getStream() { return _stream; }
private:
    asciistream &_stream;
};

}  // namespace vespalib

