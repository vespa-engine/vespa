// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "catserializer.h"
#include "rawresultnode.h"
#include "resultvector.h"
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/objects/nbostream.h>

namespace search {
namespace expression {

using vespalib::IFieldBase;
using vespalib::Serializer;
using vespalib::Deserializer;
using vespalib::string;
using vespalib::stringref;

CatSerializer & CatSerializer::put(const IFieldBase & field, const stringref & value)
{
    (void) field;
    getStream().write(value.c_str(), value.size());
    return *this;
}

CatSerializer & CatSerializer::nop(const IFieldBase & field, const void * value)
{
     (void) field;
     (void) value;
     throw vespalib::Exception("search::expression::CatSerializer can not deserialize anything as it looses information on serialize");
     return *this;
}

CatSerializer & CatSerializer::get(const IFieldBase & field, bool & value)     { return nop(field, &value); }
CatSerializer & CatSerializer::get(const IFieldBase & field, uint8_t & value)  { return nop(field, &value); }
CatSerializer & CatSerializer::get(const IFieldBase & field, uint16_t & value) { return nop(field, &value); }
CatSerializer & CatSerializer::get(const IFieldBase & field, uint32_t & value) { return nop(field, &value); }
CatSerializer & CatSerializer::get(const IFieldBase & field, uint64_t & value) { return nop(field, &value); }
CatSerializer & CatSerializer::get(const IFieldBase & field, double & value)   { return nop(field, &value); }
CatSerializer & CatSerializer::get(const IFieldBase & field, float & value)    { return nop(field, &value); }
CatSerializer & CatSerializer::get(const IFieldBase & field, string & value)   { return nop(field, &value); }

CatSerializer &  CatSerializer::put(const vespalib::IFieldBase & field, const vespalib::Identifiable & value)
{
    (void) field;
    if (value.inherits(ResultNode::classId)) {
        static_cast<const ResultNode &>(value).onSerializeResult(*this);
    } else {
        value.serializeDirect(*this);
    }
    return *this;
}

ResultSerializer &  CatSerializer::putResult(const vespalib::IFieldBase & field, const RawResultNode & value)
{
    (void) field;
    vespalib::ConstBufferRef raw(value.get());
    getStream().write(raw.c_str(), raw.size());
    return *this;
}

ResultSerializer &  CatSerializer::putResult(const vespalib::IFieldBase & field, const ResultNodeVector & value)
{
    (void) field;
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
}

// this function was added by ../../forcelink.sh
void forcelink_file_searchlib_expression_catserializer() {}
