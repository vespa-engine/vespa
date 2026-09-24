// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_string_enum_hint_search_context.hpp"

#include "string_matcher_factory.h"

namespace search::attribute {

template class SingleStringEnumHintSearchContextT<StringCasedMatcher>;
template class SingleStringEnumHintSearchContextT<StringUncasedMatcher>;
template class SingleStringEnumHintSearchContextT<StringRegexMatcher>;
template class SingleStringEnumHintSearchContextT<StringFuzzyMatcher>;
template class SingleStringEnumHintSearchContextT<StringRangeMatcher>;

} // namespace search::attribute
