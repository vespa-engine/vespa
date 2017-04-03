// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "serializer.h"
#include "deserializer.h"

namespace vespalib {

class nbostream;

class NBOSerializer : public Serializer, public Deserializer {
public:
    NBOSerializer(nbostream &stream) : _stream(stream) { }
    NBOSerializer &put(const IFieldBase &field, bool value) override;
    NBOSerializer &put(const IFieldBase &field, uint8_t value) override;
    NBOSerializer &put(const IFieldBase &field, uint16_t value) override;
    NBOSerializer &put(const IFieldBase &field, uint32_t value) override;
    NBOSerializer &put(const IFieldBase &field, uint64_t value) override;
    NBOSerializer &put(const IFieldBase &field, float value) override;
    NBOSerializer &put(const IFieldBase &field, double value) override;
    NBOSerializer &put(const IFieldBase &field, const stringref &val) override;

    NBOSerializer &get(const IFieldBase &field, bool &value) override;
    NBOSerializer &get(const IFieldBase &field, uint8_t &value) override;
    NBOSerializer &get(const IFieldBase &field, uint16_t &value) override;
    NBOSerializer &get(const IFieldBase &field, uint32_t &value) override;
    NBOSerializer &get(const IFieldBase &field, uint64_t &value) override;
    NBOSerializer &get(const IFieldBase &field, double &value) override;
    NBOSerializer &get(const IFieldBase &field, float &value) override;
    NBOSerializer &get(const IFieldBase &field, string &value) override;

    const char *peek() const;

    const nbostream &getStream() const { return _stream; }
    nbostream &getStream() { return _stream; }
private:
    nbostream &_stream;
};
}  // namespace vespalib

