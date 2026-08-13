// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "string_search_context.hpp"

#include "string_matcher.h"

namespace search::attribute {

template class StringSearchContextT<StringMatcher>;

} // namespace search::attribute
