// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_enum_search_context.hpp"
#include "string_search_context.h"
#include "numeric_range_matcher.h"
#include "numeric_search_context.h"

namespace search::attribute {

template class SingleEnumSearchContext<const char*, StringSearchContext>;
template class SingleEnumSearchContext<int8_t, NumericSearchContext<NumericRangeMatcher<int8_t>>>;
template class SingleEnumSearchContext<int16_t, NumericSearchContext<NumericRangeMatcher<int16_t>>>;
template class SingleEnumSearchContext<int32_t, NumericSearchContext<NumericRangeMatcher<int32_t>>>;
template class SingleEnumSearchContext<int64_t, NumericSearchContext<NumericRangeMatcher<int64_t>>>;
template class SingleEnumSearchContext<float, NumericSearchContext<NumericRangeMatcher<float>>>;
template class SingleEnumSearchContext<double, NumericSearchContext<NumericRangeMatcher<double>>>;

}
