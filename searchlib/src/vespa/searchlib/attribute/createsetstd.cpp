// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include "defines.h"

#include <vespa/log/log.h>
LOG_SETUP(".createsetstd");

#include <vespa/searchlib/attribute/attributevector.hpp>
#include <vespa/searchlib/attribute/multivalueattribute.hpp>
#include <vespa/searchlib/attribute/multinumericattribute.hpp>
#include <vespa/searchlib/attribute/multistringattribute.h>

namespace search {

using attribute::BasicType;

#define INTSET(T, I)   MultiValueNumericAttribute< IntegerAttributeTemplate<T>, WEIGHTED_MULTIVALUE_ARG(T, I) >
#define FLOATSET(T, I) MultiValueNumericAttribute< FloatingPointAttributeTemplate<T>, WEIGHTED_MULTIVALUE_ARG(T, I) >
#define CREATEINTSET(T, H, fname, info) H ? static_cast<AttributeVector *>(new INTSET(T, multivalue::Index64)(fname, info)) : static_cast<AttributeVector *>(new INTSET(T, multivalue::Index32)(fname, info))
#define CREATEFLOATSET(T, H, fname, info) H ? static_cast<AttributeVector *>(new FLOATSET(T, multivalue::Index64)(fname, info)) : static_cast<AttributeVector *>(new FLOATSET(T, multivalue::Index32)(fname, info))


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
        ret.reset(CREATEINTSET(int8_t, info.huge(), baseFileName, info));
        break;
    case BasicType::INT16:
        ret.reset(CREATEINTSET(int16_t, info.huge(), baseFileName, info));
        break;
    case BasicType::INT32:
        ret.reset(CREATEINTSET(int32_t, info.huge(), baseFileName, info));
        break;
    case BasicType::INT64:
        ret.reset(CREATEINTSET(int64_t, info.huge(), baseFileName, info));
        break;
    case BasicType::FLOAT:
        ret.reset(CREATEFLOATSET(float, info.huge(), baseFileName, info));
        break;
    case BasicType::DOUBLE:
        ret.reset(CREATEFLOATSET(double, info.huge(), baseFileName, info));
        break;
    case BasicType::STRING:
        ret.reset(info.huge() ? static_cast<AttributeVector *>(new HugeWeightedSetStringAttribute(baseFileName, info)) : static_cast<AttributeVector *>(new WeightedSetStringAttribute(baseFileName, info)));
        break;
    default:
        break;
    }
    return ret;
}

}
