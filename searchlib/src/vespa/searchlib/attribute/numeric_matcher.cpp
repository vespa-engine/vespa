// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "numeric_matcher.hpp"

namespace search::attribute {

template class NumericMatcher<int8_t>;
template class NumericMatcher<int16_t>;
template class NumericMatcher<int32_t>;
template class NumericMatcher<int64_t>;
template class NumericMatcher<float>;
template class NumericMatcher<double>;

}
