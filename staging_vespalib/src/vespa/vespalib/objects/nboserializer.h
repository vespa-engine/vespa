// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/serializer.h>
#include <vespa/vespalib/objects/deserializer.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace vespalib {
class NBOSerializer : public Serializer, public Deserializer {
public:
    NBOSerializer(nbostream &stream) : _stream(stream) { }
    virtual NBOSerializer &put(const IFieldBase &field, bool value);
    virtual NBOSerializer &put(const IFieldBase &field, uint8_t value);
    virtual NBOSerializer &put(const IFieldBase &field, uint16_t value);
    virtual NBOSerializer &put(const IFieldBase &field, uint32_t value);
    virtual NBOSerializer &put(const IFieldBase &field, uint64_t value);
    virtual NBOSerializer &put(const IFieldBase &field, float value);
    virtual NBOSerializer &put(const IFieldBase &field, double value);
    virtual NBOSerializer &put(const IFieldBase &field, const stringref &val);

    virtual NBOSerializer &get(const IFieldBase &field, bool &value);
    virtual NBOSerializer &get(const IFieldBase &field, uint8_t &value);
    virtual NBOSerializer &get(const IFieldBase &field, uint16_t &value);
    virtual NBOSerializer &get(const IFieldBase &field, uint32_t &value);
    virtual NBOSerializer &get(const IFieldBase &field, uint64_t &value);
    virtual NBOSerializer &get(const IFieldBase &field, double &value);
    virtual NBOSerializer &get(const IFieldBase &field, float &value);
    virtual NBOSerializer &get(const IFieldBase &field, string &value);

    const char *peek() const { return _stream.peek(); }

    const nbostream &getStream() const { return _stream; }
    nbostream &getStream() { return _stream; }
private:
    nbostream &_stream;
};
}  // namespace vespalib

