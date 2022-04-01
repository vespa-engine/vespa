// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "single_numeric_enum_search_context.hpp"

namespace search::attribute {

template class SingleNumericEnumSearchContext<int8_t>;
template class SingleNumericEnumSearchContext<int16_t>;
template class SingleNumericEnumSearchContext<int32_t>;
template class SingleNumericEnumSearchContext<int64_t>;
template class SingleNumericEnumSearchContext<float>;
template class SingleNumericEnumSearchContext<double>;

}
