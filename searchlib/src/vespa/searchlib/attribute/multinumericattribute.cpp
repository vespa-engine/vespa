// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multinumericattribute.hpp"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.multi_numeric_attribute");

namespace search {

template class MultiValueNumericAttribute<IntegerAttributeTemplate<int8_t>, int8_t>;
template class MultiValueNumericAttribute<IntegerAttributeTemplate<int16_t>, int16_t>;
template class MultiValueNumericAttribute<IntegerAttributeTemplate<int32_t>, int32_t>;
template class MultiValueNumericAttribute<IntegerAttributeTemplate<int64_t>, int64_t>;
template class MultiValueNumericAttribute<FloatingPointAttributeTemplate<float>, float>;
template class MultiValueNumericAttribute<FloatingPointAttributeTemplate<double>, double>;
template class MultiValueNumericAttribute<IntegerAttributeTemplate<int8_t>, multivalue::WeightedValue<int8_t>>;
template class MultiValueNumericAttribute<IntegerAttributeTemplate<int16_t>, multivalue::WeightedValue<int16_t>>;
template class MultiValueNumericAttribute<IntegerAttributeTemplate<int32_t>, multivalue::WeightedValue<int32_t>>;
template class MultiValueNumericAttribute<IntegerAttributeTemplate<int64_t>, multivalue::WeightedValue<int64_t>>;
template class MultiValueNumericAttribute<FloatingPointAttributeTemplate<float>, multivalue::WeightedValue<float>>;
template class MultiValueNumericAttribute<FloatingPointAttributeTemplate<double>, multivalue::WeightedValue<double>>;

} // namespace search

