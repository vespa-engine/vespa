// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "catserializer.h"
#include "rawresultnode.h"
#include "resultvector.h"
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace search::expression {

using vespalib::Serializer;
using vespalib::Deserializer;
using vespalib::string;
using vespalib::stringref;

CatSerializer & CatSerializer::put(stringref value)
{
    getStream().write(value.data(), value.size());
    return *this;
}

CatSerializer & CatSerializer::nop(const void *)
{
     throw vespalib::Exception("search::expression::CatSerializer can not deserialize anything as it looses information on serialize");
     return *this;
}

CatSerializer & CatSerializer::get(bool & value)     { return nop(&value); }
CatSerializer & CatSerializer::get(uint8_t & value)  { return nop(&value); }
CatSerializer & CatSerializer::get(uint16_t & value) { return nop(&value); }
CatSerializer & CatSerializer::get(uint32_t & value) { return nop(&value); }
CatSerializer & CatSerializer::get(uint64_t & value) { return nop(&value); }
CatSerializer & CatSerializer::get(double & value)   { return nop(&value); }
CatSerializer & CatSerializer::get(float & value)    { return nop(&value); }
CatSerializer & CatSerializer::get(string & value)   { return nop(&value); }

CatSerializer &  CatSerializer::put(const vespalib::Identifiable & value)
{
    if (value.inherits(ResultNode::classId)) {
        static_cast<const ResultNode &>(value).onSerializeResult(*this);
    } else {
        value.serializeDirect(*this);
    }
    return *this;
}

ResultSerializer &  CatSerializer::putResult(const RawResultNode & value)
{
    vespalib::ConstBufferRef raw(value.get());
    getStream().write(raw.c_str(), raw.size());
    return *this;
}

ResultSerializer &  CatSerializer::putResult(const ResultNodeVector & value)
{
    size_t sz(value.size());
    for (size_t i(0); i < sz; i++) {
        value.get(i).serialize(*this);
    }
    return *this;
}

void  CatSerializer::proxyPut(const ResultNode & value)
{
    value.serializeDirect(*this);
}

}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_catserializer() {}
