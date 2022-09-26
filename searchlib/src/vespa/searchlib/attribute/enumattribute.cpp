// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumattribute.hpp"
#include "stringbase.h"
#include "integerbase.h"
#include "floatbase.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.enum_attribute");

namespace search {

template class EnumAttribute<StringAttribute>;
template class EnumAttribute<IntegerAttributeTemplate<int8_t>>;
template class EnumAttribute<IntegerAttributeTemplate<int16_t>>;
template class EnumAttribute<IntegerAttributeTemplate<int32_t>>;
template class EnumAttribute<IntegerAttributeTemplate<int64_t>>;
template class EnumAttribute<FloatingPointAttributeTemplate<float>>;
template class EnumAttribute<FloatingPointAttributeTemplate<double>>;

} // namespace search

