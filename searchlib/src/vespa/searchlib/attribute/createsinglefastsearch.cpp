// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "integerbase.h"
#include "floatbase.h"
#include "defines.h"
#include "singlestringattribute.h"
#include "singleboolattribute.h"
#include "singlestringpostattribute.hpp"
#include "singlenumericpostattribute.hpp"
#include <vespa/searchlib/tensor/direct_tensor_attribute.h>

#define INTPOSTING(T)   SingleValueNumericPostingAttribute< ENUM_ATTRIBUTE(IntegerAttributeTemplate<T>) >
#define FLOATPOSTING(T) SingleValueNumericPostingAttribute< ENUM_ATTRIBUTE(FloatingPointAttributeTemplate<T>) >

namespace search {

using attribute::BasicType;

AttributeVector::SP
AttributeFactory::createSingleFastSearch(stringref name, const Config & info)
{
    assert(info.collectionType().type() == attribute::CollectionType::SINGLE);
    assert(info.fastSearch());
    switch(info.basicType().type()) {
    case BasicType::BOOL:
        return std::make_shared<SingleBoolAttribute>(name, info.getGrowStrategy());
    case BasicType::UINT2:
    case BasicType::UINT4:
        break;
    case BasicType::INT8:
        return std::make_shared<INTPOSTING(int8_t)>(name, info);
    case BasicType::INT16:
        return std::make_shared<INTPOSTING(int16_t)>(name, info);
    case BasicType::INT32:
        return std::make_shared<INTPOSTING(int32_t)>(name, info);
    case BasicType::INT64:
        return std::make_shared<INTPOSTING(int64_t)>(name, info);
    case BasicType::FLOAT:
        return std::make_shared<FLOATPOSTING(float)>(name, info);
    case BasicType::DOUBLE:
        return std::make_shared<FLOATPOSTING(double)>(name, info);
    case BasicType::STRING:
        return std::make_shared<SingleValueStringPostingAttribute>(name, info);
    case BasicType::TENSOR:
        if (!info.tensorType().is_dense()) {
            return std::make_shared<tensor::DirectTensorAttribute>(name, info);
        }
        break;
    default:
        break;
    }
    return AttributeVector::SP();
}

}
