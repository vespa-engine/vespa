// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/vespalib/objects/asciiserializer.h>
#include <vespa/searchlib/expression/serializer.h>


namespace search {
namespace expression {

class RawResultNode;

class StrCatSerializer : public vespalib::AsciiSerializer, public ResultSerializer
{
public:
    StrCatSerializer(vespalib::asciistream & stream) : vespalib::AsciiSerializer(stream) { }
    virtual StrCatSerializer & put(const vespalib::IFieldBase & field, const vespalib::Identifiable & value);
    virtual ResultSerializer & putResult(const vespalib::IFieldBase & field, const ResultNodeVector & value);
    virtual ResultSerializer & putResult(const vespalib::IFieldBase & field, const RawResultNode & value);
    virtual void proxyPut(const ResultNode & value);
};

}
}

