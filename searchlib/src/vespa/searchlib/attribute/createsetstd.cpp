// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "defines.h"
#include "multinumericattribute.h"
#include "multistringattribute.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.create_set_std");

namespace search {

using attribute::BasicType;

#define INTSET(T)   MultiValueNumericAttribute< IntegerAttributeTemplate<T>, WEIGHTED_MULTIVALUE_ARG(T) >
#define FLOATSET(T) MultiValueNumericAttribute< FloatingPointAttributeTemplate<T>, WEIGHTED_MULTIVALUE_ARG(T) >
#define CREATEINTSET(T, fname, info) static_cast<AttributeVector *>(new INTSET(T)(fname, info))
#define CREATEFLOATSET(T, fname, info) static_cast<AttributeVector *>(new FLOATSET(T)(fname, info))


AttributeVector::SP
AttributeFactory::createSetStd(stringref name, const Config & info)
{
    assert(info.collectionType().type() == attribute::CollectionType::WSET);
    AttributeVector::SP ret;
    switch(info.basicType().type()) {
    case BasicType::BOOL:
    case BasicType::UINT2:
    case BasicType::UINT4:
        break;
    case BasicType::INT8:
        ret.reset(CREATEINTSET(int8_t, name, info));
        break;
    case BasicType::INT16:
        ret.reset(CREATEINTSET(int16_t, name, info));
        break;
    case BasicType::INT32:
        ret.reset(CREATEINTSET(int32_t, name, info));
        break;
    case BasicType::INT64:
        ret.reset(CREATEINTSET(int64_t, name, info));
        break;
    case BasicType::FLOAT:
        ret.reset(CREATEFLOATSET(float, name, info));
        break;
    case BasicType::DOUBLE:
        ret.reset(CREATEFLOATSET(double, name, info));
        break;
    case BasicType::STRING:
        ret.reset(static_cast<AttributeVector *>(new WeightedSetStringAttribute(name, info)));
        break;
    default:
        break;
    }
    return ret;
}

}
