// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_numeric_enum_search_context.hpp"
#include <vespa/searchcommon/attribute/multivalue.h>

using ValueRef = vespalib::datastore::AtomicEntryRef;
using WeightedValueRef = search::multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>;

namespace search::attribute {

template class MultiNumericEnumSearchContext<int8_t, ValueRef>;
template class MultiNumericEnumSearchContext<int16_t, ValueRef>;
template class MultiNumericEnumSearchContext<int32_t, ValueRef>;
template class MultiNumericEnumSearchContext<int64_t, ValueRef>;
template class MultiNumericEnumSearchContext<float, ValueRef>;
template class MultiNumericEnumSearchContext<double, ValueRef>;

template class MultiNumericEnumSearchContext<int8_t, WeightedValueRef>;
template class MultiNumericEnumSearchContext<int16_t, WeightedValueRef>;
template class MultiNumericEnumSearchContext<int32_t, WeightedValueRef>;
template class MultiNumericEnumSearchContext<int64_t, WeightedValueRef>;
template class MultiNumericEnumSearchContext<float, WeightedValueRef>;
template class MultiNumericEnumSearchContext<double, WeightedValueRef>;

}
