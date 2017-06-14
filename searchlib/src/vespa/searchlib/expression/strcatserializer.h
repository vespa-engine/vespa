// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "serializer.h"
#include <vespa/vespalib/objects/asciiserializer.h>

namespace search {
namespace expression {

class RawResultNode;

class StrCatSerializer : public vespalib::AsciiSerializer, public ResultSerializer
{
public:
    StrCatSerializer(vespalib::asciistream & stream) : vespalib::AsciiSerializer(stream) { }
    StrCatSerializer & put(const vespalib::IFieldBase & field, const vespalib::Identifiable & value) override;
    ResultSerializer & putResult(const vespalib::IFieldBase & field, const ResultNodeVector & value) override;
    ResultSerializer & putResult(const vespalib::IFieldBase & field, const RawResultNode & value) override;
    void proxyPut(const ResultNode & value) override;
};

}
}

