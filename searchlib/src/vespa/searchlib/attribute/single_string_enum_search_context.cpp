// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_string_enum_search_context.hpp"

#include "string_range_matcher.h"

namespace search::attribute {

template class SingleStringEnumSearchContextT<StringMatcher>;

template class SingleStringEnumSearchContextT<StringRangeMatcher>;

} // namespace search::attribute