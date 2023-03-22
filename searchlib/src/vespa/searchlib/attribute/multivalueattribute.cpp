// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multivalueattribute.hpp"
#include "enumattribute.h"
#include "floatbase.h"
#include "integerbase.h"
#include "stringbase.h"

namespace search {

template class MultiValueAttribute<EnumAttribute<StringAttribute>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int8_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int16_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int32_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int64_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<FloatingPointAttributeTemplate<float>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<FloatingPointAttributeTemplate<double>>, vespalib::datastore::AtomicEntryRef>;

} // namespace search

