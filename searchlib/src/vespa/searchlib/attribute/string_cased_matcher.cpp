// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_cased_matcher.h"

#include "enumhintsearchcontext.h"
#include "enumstore.h"

namespace search::attribute {

StringCasedMatcher::StringCasedMatcher(std::unique_ptr<QueryTermSimple> query_term)
    : StringMatcherBase(std::move(query_term)), _helper(get_query_term()) {
}

StringCasedMatcher::StringCasedMatcher(StringCasedMatcher&&) noexcept = default;

StringCasedMatcher::~StringCasedMatcher() = default;

void StringCasedMatcher::setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store,
                                            EnumHintSearchContext&         enum_hint_sc) {
    if (is_valid()) {
        lookup_dictionary(enum_store, enum_hint_sc);
    }
}

} // namespace search::attribute
