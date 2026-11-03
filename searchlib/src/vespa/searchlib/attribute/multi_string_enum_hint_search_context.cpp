// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_string_enum_hint_search_context.hpp"

#include "string_range_matcher.h"

#include <vespa/searchcommon/attribute/multivalue.h>

using ValueRef = vespalib::datastore::AtomicEntryRef;
using WeightedValueRef = search::multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;

namespace search::attribute {

template class MultiStringEnumHintSearchContextT<ValueRef, StringMatcher>;
template class MultiStringEnumHintSearchContextT<ValueRef, StringRangeMatcher>;

template class MultiStringEnumHintSearchContextT<WeightedValueRef, StringMatcher>;
template class MultiStringEnumHintSearchContextT<WeightedValueRef, StringRangeMatcher>;

} // namespace search::attribute
