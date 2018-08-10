// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "serializer.h"
#include "deserializer.h"

namespace vespalib {

class asciistream;

class AsciiSerializer : public Serializer {
public:
    AsciiSerializer(asciistream &stream) : _stream(stream) { }
    AsciiSerializer &put(const IFieldBase &field, bool value) override;
    AsciiSerializer &put(const IFieldBase &field, uint8_t value) override;
    AsciiSerializer &put(const IFieldBase &field, uint16_t value) override;
    AsciiSerializer &put(const IFieldBase &field, uint32_t value) override;
    AsciiSerializer &put(const IFieldBase &field, uint64_t value) override;
    AsciiSerializer &put(const IFieldBase &field, float value) override;
    AsciiSerializer &put(const IFieldBase &field, double value) override;
    AsciiSerializer &put(const IFieldBase &field, stringref val) override;

    const asciistream &getStream() const { return _stream; }
    asciistream &getStream() { return _stream; }
private:
    asciistream &_stream;
};

}  // namespace vespalib

