// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include "defines.h"

#include <vespa/log/log.h>
LOG_SETUP(".createarraystd");

#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/multivalueattribute.hpp>
#include <vespa/searchlib/attribute/multinumericattribute.hpp>
#include <vespa/searchlib/attribute/multistringattribute.h>

namespace search {

using attribute::BasicType;

#define INTARRAY(T, I)   MultiValueNumericAttribute< IntegerAttributeTemplate<T>, MULTIVALUE_ARG(T, I) >
#define FLOATARRAY(T, I) MultiValueNumericAttribute< FloatingPointAttributeTemplate<T>, MULTIVALUE_ARG(T, I) >

#define CREATEINTARRAY(T, H, fname, info) H ? static_cast<AttributeVector *>(new INTARRAY(T, multivalue::Index64)(fname, info)) : static_cast<AttributeVector *>(new INTARRAY(T, multivalue::Index32)(fname, info))
#define CREATEFLOATARRAY(T, H, fname, info) H ? static_cast<AttributeVector *>(new FLOATARRAY(T, multivalue::Index64)(fname, info)) : static_cast<AttributeVector *>(new FLOATARRAY(T, multivalue::Index32)(fname, info))


AttributeVector::SP
AttributeFactory::createArrayStd(const vespalib::string & baseFileName, const Config & info)
{
    assert(info.collectionType().type() == attribute::CollectionType::ARRAY);
    AttributeVector::SP ret;
    switch(info.basicType().type()) {
    case BasicType::UINT1:
    case BasicType::UINT2:
    case BasicType::UINT4:
        break;
    case BasicType::INT8:
        ret.reset(CREATEINTARRAY(int8_t, info.huge(), baseFileName, info));
        break;
    case BasicType::INT16:
        ret.reset(CREATEINTARRAY(int16_t, info.huge(), baseFileName, info));
        break;
    case BasicType::INT32:
        ret.reset(CREATEINTARRAY(int32_t, info.huge(), baseFileName, info));
        break;
    case BasicType::INT64:
        ret.reset(CREATEINTARRAY(int64_t, info.huge(), baseFileName, info));
        break;
    case BasicType::FLOAT:
        ret.reset(CREATEFLOATARRAY(float, info.huge(), baseFileName, info));
        break;
    case BasicType::DOUBLE:
        ret.reset(CREATEFLOATARRAY(double, info.huge(), baseFileName, info));
        break;
    case BasicType::STRING:
        ret.reset(info.huge() ? static_cast<AttributeVector *>(new HugeArrayStringAttribute(baseFileName, info)) : static_cast<AttributeVector *>(new ArrayStringAttribute(baseFileName, info)));
        break;
    default:
        break;
    }
    return ret;
}

}
