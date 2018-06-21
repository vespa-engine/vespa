// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "defines.h"
#include "attributevector.hpp"
#include "multivalueattribute.hpp"
#include "multinumericattribute.hpp"
#include "multistringattribute.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.create_array_std");

namespace search {

using attribute::BasicType;

#define INTARRAY(T)   MultiValueNumericAttribute< IntegerAttributeTemplate<T>, MULTIVALUE_ARG(T) >
#define FLOATARRAY(T) MultiValueNumericAttribute< FloatingPointAttributeTemplate<T>, MULTIVALUE_ARG(T) >

#define CREATEINTARRAY(T, fname, info) static_cast<AttributeVector *>(new INTARRAY(T)(fname, info))
#define CREATEFLOATARRAY(T, fname, info) static_cast<AttributeVector *>(new FLOATARRAY(T)(fname, info))


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
        ret.reset(CREATEINTARRAY(int8_t, baseFileName, info));
        break;
    case BasicType::INT16:
        ret.reset(CREATEINTARRAY(int16_t, baseFileName, info));
        break;
    case BasicType::INT32:
        ret.reset(CREATEINTARRAY(int32_t, baseFileName, info));
        break;
    case BasicType::INT64:
        ret.reset(CREATEINTARRAY(int64_t, baseFileName, info));
        break;
    case BasicType::FLOAT:
        ret.reset(CREATEFLOATARRAY(float, baseFileName, info));
        break;
    case BasicType::DOUBLE:
        ret.reset(CREATEFLOATARRAY(double, baseFileName, info));
        break;
    case BasicType::STRING:
        ret.reset(static_cast<AttributeVector *>(new ArrayStringAttribute(baseFileName, info)));
        break;
    default:
        break;
    }
    return ret;
}

}
