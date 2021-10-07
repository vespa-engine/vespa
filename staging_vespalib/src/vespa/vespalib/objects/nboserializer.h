// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "serializer.h"
#include "deserializer.h"

namespace vespalib {

class nbostream;

class NBOSerializer : public Serializer, public Deserializer {
public:
    NBOSerializer(nbostream &stream) : _stream(stream) { }
    NBOSerializer &put(bool value) override;
    NBOSerializer &put(uint8_t value) override;
    NBOSerializer &put(uint16_t value) override;
    NBOSerializer &put(uint32_t value) override;
    NBOSerializer &put(uint64_t value) override;
    NBOSerializer &put(float value) override;
    NBOSerializer &put(double value) override;
    NBOSerializer &put(stringref val) override;

    NBOSerializer &get(bool &value) override;
    NBOSerializer &get(uint8_t &value) override;
    NBOSerializer &get(uint16_t &value) override;
    NBOSerializer &get(uint32_t &value) override;
    NBOSerializer &get(uint64_t &value) override;
    NBOSerializer &get(double &value) override;
    NBOSerializer &get(float &value) override;
    NBOSerializer &get(string &value) override;

    const char *peek() const;

    const nbostream &getStream() const { return _stream; }
    nbostream &getStream() { return _stream; }
private:
    nbostream &_stream;
};
}  // namespace vespalib

