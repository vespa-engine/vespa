// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "predicate_attribute.h"
#include "singlesmallnumericattribute.h"
#include "reference_attribute.h"
#include "singlestringattribute.h"
#include "singleboolattribute.h"
#include "singlenumericattribute.h"
#include "single_raw_attribute.h"
#include <vespa/eval/eval/fast_value.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/serialized_fast_value_attribute.h>

namespace search {

using attribute::BasicType;

AttributeVector::SP
AttributeFactory::createSingleStd(string_view name, const Config & info)
{
    assert(info.collectionType().type() == attribute::CollectionType::SINGLE);
    switch(info.basicType().type()) {
    case BasicType::BOOL:
        return std::make_shared<SingleBoolAttribute>(name, info.getGrowStrategy(), info.paged());
    case BasicType::UINT2:
        return std::make_shared<SingleValueSemiNibbleNumericAttribute>(name, info.getGrowStrategy());
    case BasicType::UINT4:
        return std::make_shared<SingleValueNibbleNumericAttribute>(name, info.getGrowStrategy());
    case BasicType::INT8:
        return std::make_shared<SingleValueNumericAttribute<IntegerAttributeTemplate<int8_t>>>(name, info);
    case BasicType::INT16:
        // XXX: Unneeded since we don't have short document fields in java.
        return std::make_shared<SingleValueNumericAttribute<IntegerAttributeTemplate<int16_t>>>(name, info);
    case BasicType::INT32:
        return std::make_shared<SingleValueNumericAttribute<IntegerAttributeTemplate<int32_t>>>(name, info);
    case BasicType::INT64:
        return std::make_shared<SingleValueNumericAttribute<IntegerAttributeTemplate<int64_t>>>(name, info);
    case BasicType::FLOAT:
        return std::make_shared<SingleValueNumericAttribute<FloatingPointAttributeTemplate<float>>>(name, info);
    case BasicType::DOUBLE:
        return std::make_shared<SingleValueNumericAttribute<FloatingPointAttributeTemplate<double>>>(name, info);
    case BasicType::STRING:
        return std::make_shared<SingleValueStringAttribute>(name, info);
    case BasicType::PREDICATE:
        return std::make_shared<PredicateAttribute>(name, info);
    case BasicType::TENSOR:
        if (info.tensorType().is_dense()) {
            return std::make_shared<tensor::DenseTensorAttribute>(name, info);
        } else {
            return std::make_shared<tensor::SerializedFastValueAttribute>(name, info);
        }
    case BasicType::REFERENCE:
        return std::make_shared<attribute::ReferenceAttribute>(name, info);
    case BasicType::RAW:
        return std::make_shared<attribute::SingleRawAttribute>(name, info);
    default:
        break;
    }
    return AttributeVector::SP();
}

}
