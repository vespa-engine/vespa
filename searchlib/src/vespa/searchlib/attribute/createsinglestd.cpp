// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "predicate_attribute.h"
#include "singlesmallnumericattribute.h"
#include "reference_attribute.h"
#include "attributevector.hpp"
#include "singlenumericattribute.hpp"
#include "singlestringattribute.h"
#include <vespa/searchlib/tensor/generic_tensor_attribute.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.create_single_std");

namespace search {

using attribute::BasicType;

AttributeVector::SP
AttributeFactory::createSingleStd(const vespalib::string & baseFileName, const Config & info)
{
    assert(info.collectionType().type() == attribute::CollectionType::SINGLE);
    AttributeVector::SP ret;
    switch(info.basicType().type()) {
    case BasicType::UINT1:
        ret.reset(new SingleValueBitNumericAttribute(baseFileName, info.getGrowStrategy()));
        break;
    case BasicType::UINT2:
        ret.reset(new SingleValueSemiNibbleNumericAttribute(baseFileName, info.getGrowStrategy()));
        break;
    case BasicType::UINT4:
        ret.reset(new SingleValueNibbleNumericAttribute(baseFileName, info.getGrowStrategy()));
        break;
    case BasicType::INT8:
        ret.reset(new SingleValueNumericAttribute<IntegerAttributeTemplate<int8_t> >(baseFileName, info));
        break;
    case BasicType::INT16:
        // XXX: Unneeded since we don't have short document fields in java.
        ret.reset(new SingleValueNumericAttribute<IntegerAttributeTemplate<int16_t> >(baseFileName, info));
        break;
    case BasicType::INT32:
        ret.reset(new SingleValueNumericAttribute<IntegerAttributeTemplate<int32_t> >(baseFileName, info));
        break;
    case BasicType::INT64:
        ret.reset(new SingleValueNumericAttribute<IntegerAttributeTemplate<int64_t> >(baseFileName, info));
        break;
    case BasicType::FLOAT:
        ret.reset(new SingleValueNumericAttribute<FloatingPointAttributeTemplate<float> >(baseFileName, info));
        break;
    case BasicType::DOUBLE:
        ret.reset(new SingleValueNumericAttribute<FloatingPointAttributeTemplate<double> >(baseFileName, info));
        break;
    case BasicType::STRING:
        ret.reset(new SingleValueStringAttribute(baseFileName, info));
        break;
    case BasicType::PREDICATE:
        ret.reset(new PredicateAttribute(baseFileName, info));
        break;
    case BasicType::TENSOR:
        if (info.tensorType().is_dense()) {
            ret.reset(new tensor::DenseTensorAttribute(baseFileName, info));
        } else {
            ret.reset(new tensor::GenericTensorAttribute(baseFileName, info));
        }
        break;
    case BasicType::REFERENCE:
        ret = std::make_shared<attribute::ReferenceAttribute>(baseFileName, info);
        break;
    default:
        break;
    }
    return ret;
}
}  // namespace search
