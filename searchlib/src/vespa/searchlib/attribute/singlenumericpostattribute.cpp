// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "singlenumericpostattribute.hpp"
#include "floatbase.h"
#include "integerbase.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.single_numeric_post_attribute");

namespace search {

template class SingleValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int8_t>>>;
template class SingleValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int16_t>>>;
template class SingleValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int32_t>>>;
template class SingleValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int64_t>>>;
template class SingleValueNumericPostingAttribute<EnumAttribute<FloatingPointAttributeTemplate<float>>>;
template class SingleValueNumericPostingAttribute<EnumAttribute<FloatingPointAttributeTemplate<double>>>;

} // namespace search

