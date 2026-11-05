// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_string_enum_hint_search_context.hpp"

#include "string_fuzzy_matcher.h"
#include "string_range_matcher.h"
#include "string_regex_matcher.h"

namespace search::attribute {

template class SingleStringEnumHintSearchContextT<StringFuzzyMatcher>;
template class SingleStringEnumHintSearchContextT<StringMatcher>;
template class SingleStringEnumHintSearchContextT<StringRangeMatcher>;
template class SingleStringEnumHintSearchContextT<StringRegexMatcher>;

} // namespace search::attribute
