// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/floatbase.h>
#include "defines.h"

#include <vespa/log/log.h>
LOG_SETUP(".createsinglefastsearch");

#include <vespa/searchlib/attribute/singlestringattribute.h>
#include <vespa/searchlib/attribute/singlestringpostattribute.hpp>
#include <vespa/searchlib/attribute/singlenumericenumattribute.hpp>
#include <vespa/searchlib/attribute/singlenumericpostattribute.hpp>
#include <vespa/searchlib/attribute/enumstore.hpp>
#include <vespa/searchlib/attribute/enumattribute.hpp>
#include <vespa/searchlib/attribute/singleenumattribute.hpp>

#define INTPOSTING(T)   SingleValueNumericPostingAttribute< ENUM_ATTRIBUTE(IntegerAttributeTemplate<T>) >
#define FLOATPOSTING(T) SingleValueNumericPostingAttribute< ENUM_ATTRIBUTE(FloatingPointAttributeTemplate<T>) >

namespace search {

using attribute::BasicType;

AttributeVector::SP
AttributeFactory::createSingleFastSearch(const vespalib::string & baseFileName, const Config & info)
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
        ret.reset(new INTPOSTING(int8_t)(baseFileName, info));
        break;
    case BasicType::INT16:
        ret.reset(new INTPOSTING(int16_t)(baseFileName, info));
        break;
    case BasicType::INT32:
        ret.reset(new INTPOSTING(int32_t)(baseFileName, info));
        break;
    case BasicType::INT64:
        ret.reset(new INTPOSTING(int64_t)(baseFileName, info));
        break;
    case BasicType::FLOAT:
        ret.reset(new FLOATPOSTING(float)(baseFileName, info));
        break;
    case BasicType::DOUBLE:
        ret.reset(new FLOATPOSTING(double)(baseFileName, info));
        break;
    case BasicType::STRING:
        ret.reset(new SingleValueStringPostingAttribute(baseFileName, info));
        break;
    default:
        break;
    }
    return ret;
}

}
