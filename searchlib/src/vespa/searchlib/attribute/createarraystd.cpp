// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "defines.h"
#include "multinumericattribute.h"
#include "multistringattribute.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.create_array_std");

namespace search {

using attribute::BasicType;

#define INTARRAY(T)   MultiValueNumericAttribute< IntegerAttributeTemplate<T>, MULTIVALUE_ARG(T) >
#define FLOATARRAY(T) MultiValueNumericAttribute< FloatingPointAttributeTemplate<T>, MULTIVALUE_ARG(T) >

#define CREATEINTARRAY(T, fname, info) static_cast<AttributeVector *>(new INTARRAY(T)(fname, info))
#define CREATEFLOATARRAY(T, fname, info) static_cast<AttributeVector *>(new FLOATARRAY(T)(fname, info))


AttributeVector::SP
AttributeFactory::createArrayStd(stringref name, const Config & info)
{
    assert(info.collectionType().type() == attribute::CollectionType::ARRAY);
    AttributeVector::SP ret;
    switch(info.basicType().type()) {
    case BasicType::BOOL:
    case BasicType::UINT2:
    case BasicType::UINT4:
        break;
    case BasicType::INT8:
        ret.reset(CREATEINTARRAY(int8_t, name, info));
        break;
    case BasicType::INT16:
        ret.reset(CREATEINTARRAY(int16_t, name, info));
        break;
    case BasicType::INT32:
        ret.reset(CREATEINTARRAY(int32_t, name, info));
        break;
    case BasicType::INT64:
        ret.reset(CREATEINTARRAY(int64_t, name, info));
        break;
    case BasicType::FLOAT:
        ret.reset(CREATEFLOATARRAY(float, name, info));
        break;
    case BasicType::DOUBLE:
        ret.reset(CREATEFLOATARRAY(double, name, info));
        break;
    case BasicType::STRING:
        ret.reset(static_cast<AttributeVector *>(new ArrayStringAttribute(name, info)));
        break;
    default:
        break;
    }
    return ret;
}

}
