// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_search_context.hpp"

#include "string_matcher_factory.h"

namespace search::attribute {

template class StringSearchContextT<StringCasedMatcher>;
template class StringSearchContextT<StringUncasedMatcher>;
template class StringSearchContextT<StringRegexMatcher>;
template class StringSearchContextT<StringFuzzyMatcher>;

template class StringSearchContextT<StringRangeMatcher>;

} // namespace search::attribute
