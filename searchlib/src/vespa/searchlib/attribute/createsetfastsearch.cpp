// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "defines.h"
#include "floatbase.h"
#include "integerbase.h"
#include "multinumericpostattribute.h"
#include "multistringpostattribute.h"
#include <vespa/searchcommon/attribute/config.h>

#include <vespa/log/log.h>
LOG_SETUP(".createsetfastsearch");

namespace search {

using attribute::BasicType;

#define INTSET(T)   MultiValueNumericPostingAttribute< ENUM_ATTRIBUTE(IntegerAttributeTemplate<T>), WEIGHTED_MULTIVALUE_ENUM_ARG >
#define FLOATSET(T) MultiValueNumericPostingAttribute< ENUM_ATTRIBUTE(FloatingPointAttributeTemplate<T>), WEIGHTED_MULTIVALUE_ENUM_ARG >

#define CREATEINTSET(T, fname, info) static_cast<AttributeVector *>(new INTSET(T)(fname, info))
#define CREATEFLOATSET(T, fname, info) static_cast<AttributeVector *>(new FLOATSET(T)(fname, info))


AttributeVector::SP
AttributeFactory::createSetFastSearch(stringref name, const Config & info)
{
    assert(info.collectionType().type() == attribute::CollectionType::WSET);
    assert(info.fastSearch());
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
        ret.reset(static_cast<AttributeVector *>(new WeightedSetStringPostingAttribute(name, info)));
        break;
    default:
        break;
    }
    return ret;
}

}
