// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "numeric_search_context.hpp"
#include "numeric_matcher.h"
#include "numeric_range_matcher.h"

namespace search::attribute {

template class NumericSearchContext<NumericMatcher<int8_t>>;
template class NumericSearchContext<NumericMatcher<int16_t>>;
template class NumericSearchContext<NumericMatcher<int32_t>>;
template class NumericSearchContext<NumericMatcher<int64_t>>;
template class NumericSearchContext<NumericMatcher<float>>;
template class NumericSearchContext<NumericMatcher<double>>;

template class NumericSearchContext<NumericRangeMatcher<int8_t>>;
template class NumericSearchContext<NumericRangeMatcher<int16_t>>;
template class NumericSearchContext<NumericRangeMatcher<int32_t>>;
template class NumericSearchContext<NumericRangeMatcher<int64_t>>;
template class NumericSearchContext<NumericRangeMatcher<float>>;
template class NumericSearchContext<NumericRangeMatcher<double>>;

}
