// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "numeric_attribute_template.h"
#include "singleboolattribute.h"
#include "singlenumericpostattribute.h"
#include "singlestringpostattribute.h"
#include "typify_basic_type.h"

#include <vespa/check_require.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/searchlib/tensor/direct_tensor_attribute.h>
#include <vespa/searchlib/tensor/direct_tensor_posting_attribute.h>

#include <type_traits>

namespace search {

using attribute::BasicType;
using attribute::TypifyBasicType;

namespace {

struct CreateSingleFastSearch {
    template <typename T> static AttributeVector::SP invoke(const std::string& name, const attribute::Config& info) {
        if constexpr (std::is_same_v<T, TypifyBasicType::String>) {
            return std::make_shared<SingleValueStringPostingAttribute>(name, info);
        } else if constexpr (std::is_same_v<T, TypifyBasicType::ComplexBasicType>) {
            return {};
        } else {
            using A = SingleValueNumericPostingAttribute<EnumAttribute<NumericAttributeTemplate<T>>>;
            return std::make_shared<A>(name, info);
        }
    }
};

} // namespace

AttributeVector::SP AttributeFactory::createSingleFastSearch(std::string name, const Config& info) {
    CHECK(info.collectionType().type() == attribute::CollectionType::SINGLE);
    CHECK(info.fastSearch());
    auto type = info.basicType().type();
    switch (type) {
    case BasicType::BOOL:
        return std::make_shared<SingleBoolAttribute>(name, info.getGrowStrategy(), info.paged());
    case BasicType::TENSOR:
        if (info.tensor_label_postings()) {
            // Throws if the tensor type is not supported
            return std::make_shared<tensor::DirectTensorPostingAttribute>(name, info);
        }
        if (!info.tensorType().is_dense()) {
            return std::make_shared<tensor::DirectTensorAttribute>(name, info);
        }
        return {};
    default:
        return vespalib::typify_invoke<1, TypifyBasicType, CreateSingleFastSearch>(type, name, info);
    }
}

} // namespace search
