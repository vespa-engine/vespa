// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "serializer.h"
#include <vespa/vespalib/objects/nboserializer.h>

namespace search::expression {

class RawResultNode;

class CatSerializer : public vespalib::NBOSerializer, public ResultSerializer
{
public:
    CatSerializer(vespalib::nbostream & stream) : vespalib::NBOSerializer(stream) { }
    CatSerializer & put(const vespalib::Identifiable & value) override;
    CatSerializer & put(vespalib::stringref value) override;
    ResultSerializer & putResult(const RawResultNode & value) override;
    ResultSerializer & putResult(const ResultNodeVector & value) override;
    void proxyPut(const ResultNode & value) override;

    CatSerializer & get(bool & value) override;
    CatSerializer & get(uint8_t & value) override;
    CatSerializer & get(uint16_t & value) override;
    CatSerializer & get(uint32_t & value) override;
    CatSerializer & get(uint64_t & value) override;
    CatSerializer & get(double & value) override;
    CatSerializer & get(float & value) override;
    CatSerializer & get(vespalib::string & value) override;

private:
    CatSerializer & nop(const void * value) __attribute__((noinline));
};

}
