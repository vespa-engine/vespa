// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_numeric_search_context.hpp"
#include "numeric_matcher.h"
#include "numeric_range_matcher.h"

namespace search::attribute {

template class SingleNumericSearchContext<int8_t, NumericMatcher<int8_t>>;
template class SingleNumericSearchContext<int16_t, NumericMatcher<int16_t>>;
template class SingleNumericSearchContext<int32_t, NumericMatcher<int32_t>>;
template class SingleNumericSearchContext<int64_t, NumericMatcher<int64_t>>;
template class SingleNumericSearchContext<float, NumericMatcher<float>>;
template class SingleNumericSearchContext<double, NumericMatcher<double>>;

template class SingleNumericSearchContext<int8_t, NumericRangeMatcher<int8_t>>;
template class SingleNumericSearchContext<int16_t, NumericRangeMatcher<int16_t>>;
template class SingleNumericSearchContext<int32_t, NumericRangeMatcher<int32_t>>;
template class SingleNumericSearchContext<int64_t, NumericRangeMatcher<int64_t>>;
template class SingleNumericSearchContext<float, NumericRangeMatcher<float>>;
template class SingleNumericSearchContext<double, NumericRangeMatcher<double>>;

}
