// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_enum_search_context.hpp"
#include "numeric_range_matcher.h"
#include "numeric_search_context.h"
#include "string_search_context.h"

using ValueRef = vespalib::datastore::AtomicEntryRef;
using WeightedValueRef = search::multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;

namespace search::attribute {

template class MultiEnumSearchContext<const char *, StringSearchContext, ValueRef>;
template class MultiEnumSearchContext<int8_t, NumericSearchContext<NumericRangeMatcher<int8_t>>, ValueRef>;
template class MultiEnumSearchContext<int16_t, NumericSearchContext<NumericRangeMatcher<int16_t>>, ValueRef>;
template class MultiEnumSearchContext<int32_t, NumericSearchContext<NumericRangeMatcher<int32_t>>, ValueRef>;
template class MultiEnumSearchContext<int64_t, NumericSearchContext<NumericRangeMatcher<int64_t>>, ValueRef>;
template class MultiEnumSearchContext<float, NumericSearchContext<NumericRangeMatcher<float>>, ValueRef>;
template class MultiEnumSearchContext<double, NumericSearchContext<NumericRangeMatcher<double>>, ValueRef>;

template class MultiEnumSearchContext<const char *, StringSearchContext, WeightedValueRef>;
template class MultiEnumSearchContext<int8_t, NumericSearchContext<NumericRangeMatcher<int8_t>>, WeightedValueRef>;
template class MultiEnumSearchContext<int16_t, NumericSearchContext<NumericRangeMatcher<int16_t>>, WeightedValueRef>;
template class MultiEnumSearchContext<int32_t, NumericSearchContext<NumericRangeMatcher<int32_t>>, WeightedValueRef>;
template class MultiEnumSearchContext<int64_t, NumericSearchContext<NumericRangeMatcher<int64_t>>, WeightedValueRef>;
template class MultiEnumSearchContext<float, NumericSearchContext<NumericRangeMatcher<float>>, WeightedValueRef>;
template class MultiEnumSearchContext<double, NumericSearchContext<NumericRangeMatcher<double>>, WeightedValueRef>;

}
