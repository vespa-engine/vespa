// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_numeric_search_context.hpp"
#include <vespa/searchcommon/attribute/multivalue.h>

using search::multivalue::WeightedValue;

namespace search::attribute {

template class MultiNumericSearchContext<int8_t, int8_t>;
template class MultiNumericSearchContext<int16_t, int16_t>;
template class MultiNumericSearchContext<int32_t, int32_t>;
template class MultiNumericSearchContext<int64_t, int64_t>;
template class MultiNumericSearchContext<float, float>;
template class MultiNumericSearchContext<double, double>;

template class MultiNumericSearchContext<int8_t, WeightedValue<int8_t>>;
template class MultiNumericSearchContext<int16_t, WeightedValue<int16_t>>;
template class MultiNumericSearchContext<int32_t, WeightedValue<int32_t>>;
template class MultiNumericSearchContext<int64_t, WeightedValue<int64_t>>;
template class MultiNumericSearchContext<float, WeightedValue<float>>;
template class MultiNumericSearchContext<double, WeightedValue<double>>;

}
