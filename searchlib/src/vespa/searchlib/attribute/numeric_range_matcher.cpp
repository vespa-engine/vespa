// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include "numeric_range_matcher.hpp"

namespace search::attribute {

template class NumericRangeMatcher<int8_t>;
template class NumericRangeMatcher<int16_t>;
template class NumericRangeMatcher<int32_t>;
template class NumericRangeMatcher<int64_t>;
template class NumericRangeMatcher<float>;
template class NumericRangeMatcher<double>;

}
