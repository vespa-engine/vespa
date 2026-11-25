// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "numeric_attribute_template.h"
#include "predicate_attribute.h"
#include "reference_attribute.h"
#include "single_raw_attribute.h"
#include "singleboolattribute.h"
#include "singlenumericattribute.h"
#include "singlestringattribute.h"
#include "typify_basic_type.h"

#include <vespa/check_require.h>
#include <vespa/eval/eval/fast_value.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/tensor/dense_tensor_attribute.h>
#include <vespa/searchlib/tensor/serialized_fast_value_attribute.h>

#include <type_traits>

namespace search {

using attribute::BasicType;
using attribute::TypifyBasicType;

namespace {

struct CreateSingleStd {
    template <typename T> static AttributeVector::SP invoke(const std::string& name, const attribute::Config& info) {
        if constexpr (std::is_same_v<T, TypifyBasicType::String>) {
            return std::make_shared<SingleValueStringAttribute>(name, info);
        } else if constexpr (std::is_same_v<T, TypifyBasicType::ComplexBasicType>) {
            return {};
        } else {
            return std::make_shared<SingleValueNumericAttribute<NumericAttributeTemplate<T>>>(name, info);
        }
    }
};

} // namespace

AttributeVector::SP AttributeFactory::createSingleStd(std::string name, const Config& info) {
    CHECK(info.collectionType().type() == attribute::CollectionType::SINGLE);
    auto type = info.basicType().type();
    switch (type) {
    case BasicType::BOOL:
        return std::make_shared<SingleBoolAttribute>(name, info.getGrowStrategy(), info.paged());
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
        return vespalib::typify_invoke<1, TypifyBasicType, CreateSingleStd>(type, name, info);
    }
}

} // namespace search
