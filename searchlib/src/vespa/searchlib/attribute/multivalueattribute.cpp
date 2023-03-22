// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multivalueattribute.hpp"
#include "enumattribute.h"
#include "floatbase.h"
#include "integerbase.h"
#include "stringbase.h"

namespace search {

template class MultiValueAttribute<IntegerAttributeTemplate<int8_t>, int8_t>;
template class MultiValueAttribute<IntegerAttributeTemplate<int16_t>, int16_t>;
template class MultiValueAttribute<IntegerAttributeTemplate<int32_t>, int32_t>;
template class MultiValueAttribute<IntegerAttributeTemplate<int64_t>, int64_t>;
template class MultiValueAttribute<FloatingPointAttributeTemplate<float>, float>;
template class MultiValueAttribute<FloatingPointAttributeTemplate<double>, double>;
template class MultiValueAttribute<IntegerAttributeTemplate<int8_t>, multivalue::WeightedValue<int8_t>>;
template class MultiValueAttribute<IntegerAttributeTemplate<int16_t>, multivalue::WeightedValue<int16_t>>;
template class MultiValueAttribute<IntegerAttributeTemplate<int32_t>, multivalue::WeightedValue<int32_t>>;
template class MultiValueAttribute<IntegerAttributeTemplate<int64_t>, multivalue::WeightedValue<int64_t>>;
template class MultiValueAttribute<FloatingPointAttributeTemplate<float>, multivalue::WeightedValue<float>>;
template class MultiValueAttribute<FloatingPointAttributeTemplate<double>, multivalue::WeightedValue<double>>;
template class MultiValueAttribute<EnumAttribute<StringAttribute>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int8_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int16_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int32_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int64_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<FloatingPointAttributeTemplate<float>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<FloatingPointAttributeTemplate<double>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueAttribute<EnumAttribute<StringAttribute>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int8_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int16_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int32_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueAttribute<EnumAttribute<IntegerAttributeTemplate<int64_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueAttribute<EnumAttribute<FloatingPointAttributeTemplate<float>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueAttribute<EnumAttribute<FloatingPointAttributeTemplate<double>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;

} // namespace search

