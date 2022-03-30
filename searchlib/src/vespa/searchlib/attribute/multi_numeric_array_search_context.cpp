// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_numeric_array_search_context.hpp"
#include <vespa/searchcommon/attribute/multivalue.h>

using search::multivalue::Value;

namespace search::attribute {

template class MultiNumericArraySearchContext<int8_t, Value<int8_t>>;
template class MultiNumericArraySearchContext<int16_t, Value<int16_t>>;
template class MultiNumericArraySearchContext<int32_t, Value<int32_t>>;
template class MultiNumericArraySearchContext<int64_t, Value<int64_t>>;
template class MultiNumericArraySearchContext<float, Value<float>>;
template class MultiNumericArraySearchContext<double, Value<double>>;

}
