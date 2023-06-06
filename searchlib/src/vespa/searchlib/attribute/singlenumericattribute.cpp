// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singlenumericattribute.hpp"

namespace search {

template class SingleValueNumericAttribute<IntegerAttributeTemplate<int8_t>>;
template class SingleValueNumericAttribute<IntegerAttributeTemplate<int16_t>>;
template class SingleValueNumericAttribute<IntegerAttributeTemplate<int32_t>>;
template class SingleValueNumericAttribute<IntegerAttributeTemplate<int64_t>>;
template class SingleValueNumericAttribute<FloatingPointAttributeTemplate<float>>;
template class SingleValueNumericAttribute<FloatingPointAttributeTemplate<double>>;

}
