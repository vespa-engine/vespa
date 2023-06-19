// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_value_mapping.hpp"
#include "attributevector.h"
#include "i_enum_store.h"
#include <vespa/searchcommon/attribute/multivalue.h>
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/array_store_dynamic_type_mapper.hpp>
#include <vespa/vespalib/datastore/dynamic_array_buffer_type.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/util/array.hpp>

using search::multivalue::WeightedValue;

namespace search::attribute {

template class MultiValueMapping<vespalib::datastore::AtomicEntryRef>;
template class MultiValueMapping<WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueMapping<int8_t>;
template class MultiValueMapping<WeightedValue<int8_t>>;
template class MultiValueMapping<int16_t>;
template class MultiValueMapping<WeightedValue<int16_t>>;
template class MultiValueMapping<int32_t>;
template class MultiValueMapping<WeightedValue<int32_t>>;
template class MultiValueMapping<int64_t>;
template class MultiValueMapping<WeightedValue<int64_t>>;
template class MultiValueMapping<float>;
template class MultiValueMapping<WeightedValue<float>>;
template class MultiValueMapping<double>;
template class MultiValueMapping<WeightedValue<double>>;

}
