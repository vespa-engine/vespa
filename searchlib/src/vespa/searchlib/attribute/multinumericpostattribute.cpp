// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multinumericpostattribute.hpp"
#include "enumattribute.h"
#include "floatbase.h"
#include "integerbase.h"

namespace search {

template class MultiValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int8_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int16_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int32_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int64_t>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericPostingAttribute<EnumAttribute<FloatingPointAttributeTemplate<float>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericPostingAttribute<EnumAttribute<FloatingPointAttributeTemplate<double>>, vespalib::datastore::AtomicEntryRef>;
template class MultiValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int8_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int16_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int32_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueNumericPostingAttribute<EnumAttribute<IntegerAttributeTemplate<int64_t>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueNumericPostingAttribute<EnumAttribute<FloatingPointAttributeTemplate<float>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueNumericPostingAttribute<EnumAttribute<FloatingPointAttributeTemplate<double>>, multivalue::WeightedValue<vespalib::datastore::AtomicEntryRef>>;

} // namespace search

