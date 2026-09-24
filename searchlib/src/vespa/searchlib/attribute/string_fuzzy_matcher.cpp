// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_fuzzy_matcher.h"

#include "enumhintsearchcontext.h"
#include "enumstore.h"

namespace search::attribute {

StringFuzzyMatcher::StringFuzzyMatcher(std::unique_ptr<QueryTermSimple> query_term, bool cased,
                                       vespalib::FuzzyMatchingAlgorithm fuzzy_matching_algorithm)
    : StringMatcherBase(std::move(query_term)), _helper(get_query_term(), cased, fuzzy_matching_algorithm) {
}

StringFuzzyMatcher::StringFuzzyMatcher(StringFuzzyMatcher&&) noexcept = default;

StringFuzzyMatcher::~StringFuzzyMatcher() = default;

void StringFuzzyMatcher::setup_enum_hint_sc(const EnumStoreT<const char*>& enum_store,
                                            EnumHintSearchContext&         enum_hint_sc) {
    if (is_valid()) {
        lookup_dictionary(enum_store, enum_hint_sc);
    }
}

} // namespace search::attribute
