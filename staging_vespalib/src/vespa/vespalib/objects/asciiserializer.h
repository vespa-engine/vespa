// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/serializer.h>
#include <vespa/vespalib/objects/deserializer.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace vespalib {
class AsciiSerializer : public Serializer {
public:
    AsciiSerializer(asciistream &stream) : _stream(stream) { }
    virtual AsciiSerializer &put(const IFieldBase &field, bool value);
    virtual AsciiSerializer &put(const IFieldBase &field, uint8_t value);
    virtual AsciiSerializer &put(const IFieldBase &field, uint16_t value);
    virtual AsciiSerializer &put(const IFieldBase &field, uint32_t value);
    virtual AsciiSerializer &put(const IFieldBase &field, uint64_t value);
    virtual AsciiSerializer &put(const IFieldBase &field, float value);
    virtual AsciiSerializer &put(const IFieldBase &field, double value);
    virtual AsciiSerializer &put(const IFieldBase &field, const stringref &val);

    const asciistream &getStream() const { return _stream; }
    asciistream &getStream() { return _stream; }
private:
    asciistream &_stream;
};

}  // namespace vespalib

