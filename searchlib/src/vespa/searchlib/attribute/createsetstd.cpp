// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "multinumericattribute.h"
#include "multistringattribute.h"
#include "numeric_attribute_template.h"
#include "typify_basic_type.h"

#include <vespa/check_require.h>

#include <type_traits>

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.create_set_std");

namespace search {

using attribute::TypifyBasicType;

namespace {

struct CreateSetStd {
    template <typename T> static AttributeVector::SP invoke(const std::string& name, const attribute::Config& info) {
        if constexpr (std::is_same_v<T, TypifyBasicType::String>) {
            return std::make_shared<WeightedSetStringAttribute>(name, info);
        } else if constexpr (std::is_same_v<T, TypifyBasicType::ComplexBasicType>) {
            return {};
        } else {
            return std::make_shared<
                MultiValueNumericAttribute<NumericAttributeTemplate<T>, multivalue::WeightedValue<T>>>(name, info);
        }
    }
};

} // namespace

AttributeVector::SP AttributeFactory::createSetStd(std::string name, const Config& info) {
    CHECK(info.collectionType().type() == attribute::CollectionType::WSET);
    return vespalib::typify_invoke<1, TypifyBasicType, CreateSetStd>(info.basicType().type(), name, info);
}

} // namespace search
