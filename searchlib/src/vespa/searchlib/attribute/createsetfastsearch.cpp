// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributefactory.h"
#include "multinumericpostattribute.h"
#include "multistringpostattribute.h"
#include "numeric_attribute_template.h"
#include "typify_basic_type.h"

#include <vespa/check_require.h>
#include <vespa/searchcommon/attribute/config.h>

#include <type_traits>

#include <vespa/log/log.h>
LOG_SETUP(".createsetfastsearch");

namespace search {

using attribute::TypifyBasicType;

namespace {

struct CreateSetFastSearch {
    template <typename T> static AttributeVector::SP invoke(const std::string& name, const attribute::Config& info) {
        if constexpr (std::is_same_v<T, TypifyBasicType::String>) {
            return std::make_shared<WeightedSetStringPostingAttribute>(name, info);
        } else if constexpr (std::is_same_v<T, TypifyBasicType::ComplexBasicType>) {
            return {};
        } else {
            using A =
                MultiValueNumericPostingAttribute<EnumAttribute<NumericAttributeTemplate<T>>,
                                                  multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
            return std::make_shared<A>(name, info);
        }
    }
};

} // namespace

AttributeVector::SP AttributeFactory::createSetFastSearch(std::string name, const Config& info) {
    CHECK(info.collectionType().type() == attribute::CollectionType::WSET);
    CHECK(info.fastSearch());
    return vespalib::typify_invoke<1, TypifyBasicType, CreateSetFastSearch>(info.basicType().type(), name, info);
}

} // namespace search
