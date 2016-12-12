// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "strcatserializer.h"
#include "rawresultnode.h"
#include "resultvector.h"
#include <vespa/vespalib/stllike/asciistream.h>

namespace search {
namespace expression {

using vespalib::IFieldBase;
using vespalib::Serializer;
using vespalib::string;
using vespalib::stringref;

StrCatSerializer &  StrCatSerializer::put(const vespalib::IFieldBase & field, const vespalib::Identifiable & value)
{
    (void) field;
    if (value.inherits(ResultNode::classId)) {
        static_cast<const ResultNode &>(value).onSerializeResult(*this);
    } else {
        value.serializeDirect(*this);
    }
    return *this;
}

ResultSerializer &  StrCatSerializer::putResult(const vespalib::IFieldBase & field, const ResultNodeVector & value)
{
    (void) field;
    size_t sz(value.size());
    for (size_t i(0); i < sz; i++) {
        value.get(i).serialize(*this);
    }
    return *this;
}

ResultSerializer &  StrCatSerializer::putResult(const vespalib::IFieldBase & field, const RawResultNode & value)
{
    (void) field;
    vespalib::ConstBufferRef buf(value.get());
    getStream() << stringref(buf.c_str(), buf.size());
    return *this;
}

void  StrCatSerializer::proxyPut(const ResultNode & value)
{
    value.serializeDirect(*this);
}

}
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_strcatserializer() {}
