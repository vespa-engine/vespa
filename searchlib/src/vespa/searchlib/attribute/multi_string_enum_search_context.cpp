// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_string_enum_search_context.hpp"

#include "string_fuzzy_matcher.h"
#include "string_range_matcher.h"
#include "string_regex_matcher.h"

#include <vespa/searchcommon/attribute/multivalue.h>

using ValueRef = vespalib::datastore::AtomicEntryRef;
using WeightedValueRef = search::multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;

namespace search::attribute {

template class MultiStringEnumSearchContextT<ValueRef, StringFuzzyMatcher>;
template class MultiStringEnumSearchContextT<ValueRef, StringMatcher>;
template class MultiStringEnumSearchContextT<ValueRef, StringRangeMatcher>;
template class MultiStringEnumSearchContextT<ValueRef, StringRegexMatcher>;

template class MultiStringEnumSearchContextT<WeightedValueRef, StringFuzzyMatcher>;
template class MultiStringEnumSearchContextT<WeightedValueRef, StringMatcher>;
template class MultiStringEnumSearchContextT<WeightedValueRef, StringRangeMatcher>;
template class MultiStringEnumSearchContextT<WeightedValueRef, StringRegexMatcher>;

} // namespace search::attribute
