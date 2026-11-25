// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "array_bool_attribute.h"
#include "attributefactory.h"
#include "multinumericattribute.h"
#include "multistringattribute.h"
#include "numeric_attribute_template.h"
#include "typify_basic_type.h"

#include <vespa/check_require.h>

#include <type_traits>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.create_array_std");

namespace search {

using attribute::BasicType;
using attribute::TypifyBasicType;

namespace {

struct CreateArrayStd {
    template <typename T> static AttributeVector::SP invoke(const std::string& name, const attribute::Config& info) {
        if constexpr (std::is_same_v<T, TypifyBasicType::String>) {
            return std::make_shared<ArrayStringAttribute>(name, info);
        } else if constexpr (std::is_same_v<T, TypifyBasicType::ComplexBasicType>) {
            return {};
        } else {
            return std::make_shared<MultiValueNumericAttribute<NumericAttributeTemplate<T>, T>>(name, info);
        }
    }
};

} // namespace

AttributeVector::SP AttributeFactory::createArrayStd(std::string name, const Config& info) {
    CHECK(info.collectionType().type() == attribute::CollectionType::ARRAY);
    if (info.basicType().type() == BasicType::BOOL) {
        return std::make_shared<attribute::ArrayBoolAttribute>(name, info);
    }
    return vespalib::typify_invoke<1, TypifyBasicType, CreateArrayStd>(info.basicType().type(), name, info);
}

} // namespace search
