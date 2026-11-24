// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "flagattribute.h"
#include "multinumericpostattribute.h"
#include "multistringpostattribute.h"
#include "numeric_attribute_template.h"
#include "typify_basic_type.h"

#include <vespa/check_require.h>
#include <vespa/searchcommon/attribute/config.h>

#include <type_traits>

#include <vespa/log/log.h>
LOG_SETUP(".createarrayfastsearch");

namespace search {

using attribute::TypifyBasicType;

namespace {

struct CreateArrayFastSearch {
    template <typename T> static AttributeVector::SP invoke(const std::string& name, const attribute::Config& info) {
        if constexpr (std::is_same_v<T, TypifyBasicType::String>) {
            return std::make_shared<ArrayStringPostingAttribute>(name, info);
        } else if constexpr (std::is_same_v<T, TypifyBasicType::Unsupported>) {
            return {};
        } else if constexpr (std::is_same_v<T, int8_t>) {
            // Fast search int8 arrays use FlagAttribute, which keeps one bit vector per
            // possible value (256 in all) instead of an enum store with posting lists.
            return std::make_shared<FlagAttribute>(name, info);
        } else {
            using A = MultiValueNumericPostingAttribute<EnumAttribute<NumericAttributeTemplate<T>>,
                                                        vespalib::datastore::AtomicEntryRef>;
            return std::make_shared<A>(name, info);
        }
    }
};

} // namespace

AttributeVector::SP AttributeFactory::createArrayFastSearch(std::string name, const Config& info) {
    CHECK(info.collectionType().type() == attribute::CollectionType::ARRAY);
    CHECK(info.fastSearch());
    return vespalib::typify_invoke<1, TypifyBasicType, CreateArrayFastSearch>(info.basicType().type(), name, info);
}

} // namespace search
