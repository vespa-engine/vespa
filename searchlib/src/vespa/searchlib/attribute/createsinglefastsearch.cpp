// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "integerbase.h"
#include "floatbase.h"
#include "defines.h"
#include "singlestringattribute.h"
#include "singlestringpostattribute.hpp"
#include "singlenumericenumattribute.hpp"
#include "singlenumericpostattribute.hpp"
#include "enumstore.hpp"
#include "enumattribute.hpp"
#include "singleenumattribute.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.create_single_fast_search");

#define INTPOSTING(T)   SingleValueNumericPostingAttribute< ENUM_ATTRIBUTE(IntegerAttributeTemplate<T>) >
#define FLOATPOSTING(T) SingleValueNumericPostingAttribute< ENUM_ATTRIBUTE(FloatingPointAttributeTemplate<T>) >

namespace search {

using attribute::BasicType;

AttributeVector::SP
AttributeFactory::createSingleFastSearch(stringref name, const Config & info)
{
    assert(info.collectionType().type() == attribute::CollectionType::SINGLE);
    assert(info.fastSearch());
    AttributeVector::SP ret;
    switch(info.basicType().type()) {
    case BasicType::UINT1:
    case BasicType::UINT2:
    case BasicType::UINT4:
        break;
    case BasicType::INT8:
        ret.reset(new INTPOSTING(int8_t)(name, info));
        break;
    case BasicType::INT16:
        ret.reset(new INTPOSTING(int16_t)(name, info));
        break;
    case BasicType::INT32:
        ret.reset(new INTPOSTING(int32_t)(name, info));
        break;
    case BasicType::INT64:
        ret.reset(new INTPOSTING(int64_t)(name, info));
        break;
    case BasicType::FLOAT:
        ret.reset(new FLOATPOSTING(float)(name, info));
        break;
    case BasicType::DOUBLE:
        ret.reset(new FLOATPOSTING(double)(name, info));
        break;
    case BasicType::STRING:
        ret.reset(new SingleValueStringPostingAttribute(name, info));
        break;
    default:
        break;
    }
    return ret;
}

}
