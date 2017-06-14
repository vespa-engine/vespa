// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "serializer.h"
#include <vespa/vespalib/objects/nboserializer.h>

namespace search {
namespace expression {

class RawResultNode;

class CatSerializer : public vespalib::NBOSerializer, public ResultSerializer
{
public:
    CatSerializer(vespalib::nbostream & stream) : vespalib::NBOSerializer(stream) { }
    CatSerializer & put(const vespalib::IFieldBase & field, const vespalib::Identifiable & value) override;
    CatSerializer & put(const vespalib::IFieldBase & field, const vespalib::stringref & value) override;
    ResultSerializer & putResult(const vespalib::IFieldBase & field, const RawResultNode & value) override;
    ResultSerializer & putResult(const vespalib::IFieldBase & field, const ResultNodeVector & value) override;
    void proxyPut(const ResultNode & value) override;

    CatSerializer & get(const vespalib::IFieldBase & field, bool & value) override;
    CatSerializer & get(const vespalib::IFieldBase & field, uint8_t & value) override;
    CatSerializer & get(const vespalib::IFieldBase & field, uint16_t & value) override;
    CatSerializer & get(const vespalib::IFieldBase & field, uint32_t & value) override;
    CatSerializer & get(const vespalib::IFieldBase & field, uint64_t & value) override;
    CatSerializer & get(const vespalib::IFieldBase & field, double & value) override;
    CatSerializer & get(const vespalib::IFieldBase & field, float & value) override;
    CatSerializer & get(const vespalib::IFieldBase & field, vespalib::string & value) override;

private:
    CatSerializer & nop(const vespalib::IFieldBase & field, const void * value) __attribute__((noinline));
};

}
}
