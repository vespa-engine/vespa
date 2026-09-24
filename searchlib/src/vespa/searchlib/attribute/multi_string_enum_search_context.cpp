// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_string_enum_search_context.hpp"

#include "string_matcher_factory.h"

#include <vespa/searchcommon/attribute/multivalue.h>

using ValueRef = vespalib::datastore::AtomicEntryRef;
using WeightedValueRef = search::multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;

namespace search::attribute {

template class MultiStringEnumSearchContextT<ValueRef, StringCasedMatcher>;
template class MultiStringEnumSearchContextT<ValueRef, StringUncasedMatcher>;
template class MultiStringEnumSearchContextT<ValueRef, StringRegexMatcher>;
template class MultiStringEnumSearchContextT<ValueRef, StringFuzzyMatcher>;
template class MultiStringEnumSearchContextT<ValueRef, StringRangeMatcher>;

template class MultiStringEnumSearchContextT<WeightedValueRef, StringCasedMatcher>;
template class MultiStringEnumSearchContextT<WeightedValueRef, StringUncasedMatcher>;
template class MultiStringEnumSearchContextT<WeightedValueRef, StringRegexMatcher>;
template class MultiStringEnumSearchContextT<WeightedValueRef, StringFuzzyMatcher>;
template class MultiStringEnumSearchContextT<WeightedValueRef, StringRangeMatcher>;

} // namespace search::attribute
