// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/nboserializer.h>
#include <vespa/searchlib/expression/serializer.h>


namespace search {
namespace expression {

class RawResultNode;

class CatSerializer : public vespalib::NBOSerializer, public ResultSerializer
{
public:
    CatSerializer(vespalib::nbostream & stream) : vespalib::NBOSerializer(stream) { }
    virtual CatSerializer & put(const vespalib::IFieldBase & field, const vespalib::Identifiable & value);
    virtual CatSerializer & put(const vespalib::IFieldBase & field, const vespalib::stringref & value);
    virtual ResultSerializer & putResult(const vespalib::IFieldBase & field, const RawResultNode & value);
    virtual ResultSerializer & putResult(const vespalib::IFieldBase & field, const ResultNodeVector & value);
    virtual void proxyPut(const ResultNode & value);

    virtual CatSerializer & get(const vespalib::IFieldBase & field, bool & value);
    virtual CatSerializer & get(const vespalib::IFieldBase & field, uint8_t & value);
    virtual CatSerializer & get(const vespalib::IFieldBase & field, uint16_t & value);
    virtual CatSerializer & get(const vespalib::IFieldBase & field, uint32_t & value);
    virtual CatSerializer & get(const vespalib::IFieldBase & field, uint64_t & value);
    virtual CatSerializer & get(const vespalib::IFieldBase & field, double & value);
    virtual CatSerializer & get(const vespalib::IFieldBase & field, float & value);
    virtual CatSerializer & get(const vespalib::IFieldBase & field, vespalib::string & value);

private:
    CatSerializer & nop(const vespalib::IFieldBase & field, const void * value) __attribute__((noinline));
};

}
}

