// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "defines.h"
#include "floatbase.h"
#include "integerbase.h"
#include "singleboolattribute.h"
#include "singlenumericpostattribute.h"
#include "singlestringpostattribute.h"
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/tensor/direct_tensor_attribute.h>

#define INTPOSTING(T)   SingleValueNumericPostingAttribute< ENUM_ATTRIBUTE(IntegerAttributeTemplate<T>) >
#define FLOATPOSTING(T) SingleValueNumericPostingAttribute< ENUM_ATTRIBUTE(FloatingPointAttributeTemplate<T>) >

namespace search {

using attribute::BasicType;

AttributeVector::SP
AttributeFactory::createSingleFastSearch(string_view name, const Config & info)
{
    assert(info.collectionType().type() == attribute::CollectionType::SINGLE);
    assert(info.fastSearch());
    switch(info.basicType().type()) {
    case BasicType::BOOL:
        return std::make_shared<SingleBoolAttribute>(name, info.getGrowStrategy(), info.paged());
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
