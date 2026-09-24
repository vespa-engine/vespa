// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_regex_matcher.h"

#include "enumhintsearchcontext.h"
#include "enumstore.h"

#include <vespa/vespalib/util/regexp.h>

namespace search::attribute {

StringRegexMatcher::StringRegexMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased)
    : StringMatcherBase(std::move(query_term)), _helper(get_query_term(), cased) {
}

StringRegexMatcher::StringRegexMatcher(StringRegexMatcher&&) noexcept = default;

StringRegexMatcher::~StringRegexMatcher() = default;

std::string StringRegexMatcher::get_prefix() const {
    return vespalib::RegexpUtil::get_prefix(get_query_term().getTerm());
}

void StringRegexMatcher::setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store,
                                            EnumHintSearchContext&         enum_hint_sc) {
    if (is_valid()) {
        lookup_dictionary(enum_store, enum_hint_sc);
    }
}

} // namespace search::attribute
