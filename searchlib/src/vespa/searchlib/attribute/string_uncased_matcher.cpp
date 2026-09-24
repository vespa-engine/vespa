// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_uncased_matcher.h"

#include "enumhintsearchcontext.h"
#include "enumstore.h"

namespace search::attribute {

StringUncasedMatcher::StringUncasedMatcher(std::unique_ptr<QueryTermSimple> query_term)
    : StringMatcherBase(std::move(query_term)), _helper(get_query_term()) {
}

StringUncasedMatcher::StringUncasedMatcher(StringUncasedMatcher&&) noexcept = default;

StringUncasedMatcher::~StringUncasedMatcher() = default;

void StringUncasedMatcher::setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store,
                                              EnumHintSearchContext&         enum_hint_sc) {
    if (is_valid()) {
        lookup_dictionary(enum_store, enum_hint_sc);
    }
}

} // namespace search::attribute
