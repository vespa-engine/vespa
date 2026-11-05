// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_search_context.hpp"

#include "string_fuzzy_matcher.h"
#include "string_matcher.h"
#include "string_range_matcher.h"
#include "string_regex_matcher.h"

namespace search::attribute {

template class StringSearchContextT<StringFuzzyMatcher>;
template class StringSearchContextT<StringMatcher>;
template class StringSearchContextT<StringRangeMatcher>;
template class StringSearchContextT<StringRegexMatcher>;

} // namespace search::attribute
