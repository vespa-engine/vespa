// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multinumericenumattribute.hpp"
#include "enumattribute.h"
#include "floatbase.h"
#include "integerbase.h"

#include <vespa/log/log.h>
LOG_SETUP(".searchlib.attribute.multi_numeric_enum_attribute");

namespace search {

template class MultiValueNumericEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int8_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int16_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int32_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int64_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericEnumAttribute<EnumAttribute<FloatingPointAttributeTemplate<float>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericEnumAttribute<EnumAttribute<FloatingPointAttributeTemplate<double>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int8_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueNumericEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int16_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueNumericEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int32_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueNumericEnumAttribute<EnumAttribute<IntegerAttributeTemplate<int64_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueNumericEnumAttribute<EnumAttribute<FloatingPointAttributeTemplate<float>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueNumericEnumAttribute<EnumAttribute<FloatingPointAttributeTemplate<double>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;

} // namespace search

