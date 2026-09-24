// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_string_enum_search_context.hpp"

#include "string_matcher_factory.h"

namespace search::attribute {

template class SingleStringEnumSearchContextT<StringCasedMatcher>;
template class SingleStringEnumSearchContextT<StringUncasedMatcher>;
template class SingleStringEnumSearchContextT<StringRegexMatcher>;
template class SingleStringEnumSearchContextT<StringFuzzyMatcher>;

template class SingleStringEnumSearchContextT<StringRangeMatcher>;

} // namespace search::attribute
