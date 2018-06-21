// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "defines.h"
#include "attributevector.hpp"
#include "multivalueattribute.hpp"
#include "multinumericattribute.hpp"
#include "multistringattribute.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.create_set_std");

namespace search {

using attribute::BasicType;

#define INTSET(T)   MultiValueNumericAttribute< IntegerAttributeTemplate<T>, WEIGHTED_MULTIVALUE_ARG(T) >
#define FLOATSET(T) MultiValueNumericAttribute< FloatingPointAttributeTemplate<T>, WEIGHTED_MULTIVALUE_ARG(T) >
#define CREATEINTSET(T, fname, info) static_cast<AttributeVector *>(new INTSET(T)(fname, info))
#define CREATEFLOATSET(T, fname, info) static_cast<AttributeVector *>(new FLOATSET(T)(fname, info))


AttributeVector::SP
AttributeFactory::createSetStd(const vespalib::string & baseFileName, const Config & info)
{
    assert(info.collectionType().type() == attribute::CollectionType::WSET);
    AttributeVector::SP ret;
    switch(info.basicType().type()) {
    case BasicType::UINT1:
    case BasicType::UINT2:
    case BasicType::UINT4:
        break;
    case BasicType::INT8:
        ret.reset(CREATEINTSET(int8_t, baseFileName, info));
        break;
    case BasicType::INT16:
        ret.reset(CREATEINTSET(int16_t, baseFileName, info));
        break;
    case BasicType::INT32:
        ret.reset(CREATEINTSET(int32_t, baseFileName, info));
        break;
    case BasicType::INT64:
        ret.reset(CREATEINTSET(int64_t, baseFileName, info));
        break;
    case BasicType::FLOAT:
        ret.reset(CREATEFLOATSET(float, baseFileName, info));
        break;
    case BasicType::DOUBLE:
        ret.reset(CREATEFLOATSET(double, baseFileName, info));
        break;
    case BasicType::STRING:
        ret.reset(static_cast<AttributeVector *>(new WeightedSetStringAttribute(baseFileName, info)));
        break;
    default:
        break;
    }
    return ret;
}

}
