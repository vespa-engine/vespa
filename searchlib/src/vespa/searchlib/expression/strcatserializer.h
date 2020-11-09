// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "serializer.h"
#include <vespa/vespalib/objects/asciiserializer.h>

namespace search::expression {

class RawResultNode;

class StrCatSerializer : public vespalib::AsciiSerializer, public ResultSerializer
{
public:
    StrCatSerializer(vespalib::asciistream & stream) : vespalib::AsciiSerializer(stream) { }
    StrCatSerializer & put(const vespalib::Identifiable & value) override;
    ResultSerializer & putResult(const ResultNodeVector & value) override;
    ResultSerializer & putResult(const RawResultNode & value) override;
    void proxyPut(const ResultNode & value) override;
};

}
