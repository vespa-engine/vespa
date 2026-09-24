// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_string_enum_hint_search_context.hpp"

#include "string_matcher_factory.h"

#include <vespa/searchcommon/attribute/multivalue.h>

using ValueRef = vespalib::datastore::AtomicEntryRef;
using WeightedValueRef = search::multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;

namespace search::attribute {

template class MultiStringEnumHintSearchContextT<ValueRef, StringCasedMatcher>;
template class MultiStringEnumHintSearchContextT<ValueRef, StringUncasedMatcher>;
template class MultiStringEnumHintSearchContextT<ValueRef, StringRegexMatcher>;
template class MultiStringEnumHintSearchContextT<ValueRef, StringFuzzyMatcher>;
template class MultiStringEnumHintSearchContextT<ValueRef, StringRangeMatcher>;

template class MultiStringEnumHintSearchContextT<WeightedValueRef, StringCasedMatcher>;
template class MultiStringEnumHintSearchContextT<WeightedValueRef, StringUncasedMatcher>;
template class MultiStringEnumHintSearchContextT<WeightedValueRef, StringRegexMatcher>;
template class MultiStringEnumHintSearchContextT<WeightedValueRef, StringFuzzyMatcher>;
template class MultiStringEnumHintSearchContextT<WeightedValueRef, StringRangeMatcher>;

} // namespace search::attribute
