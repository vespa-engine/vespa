// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_numeric_weighted_set_search_context.hpp"
#include <vespa/searchcommon/attribute/multivalue.h>

using search::multivalue::WeightedValue;

namespace search::attribute {

template class MultiNumericWeightedSetSearchContext<int8_t, WeightedValue<int8_t>>;
template class MultiNumericWeightedSetSearchContext<int16_t, WeightedValue<int16_t>>;
template class MultiNumericWeightedSetSearchContext<int32_t, WeightedValue<int32_t>>;
template class MultiNumericWeightedSetSearchContext<int64_t, WeightedValue<int64_t>>;
template class MultiNumericWeightedSetSearchContext<float, WeightedValue<float>>;
template class MultiNumericWeightedSetSearchContext<double, WeightedValue<double>>;

}
