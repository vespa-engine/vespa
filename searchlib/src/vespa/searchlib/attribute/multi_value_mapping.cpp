// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributevector.h"
#include "i_enum_store.h"
#include "multi_value_mapping.h"
#include "multi_value_mapping.hpp"
#include <vespa/searchcommon/attribute/multivalue.h>
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/buffer_type.hpp>
#include <vespa/vespalib/util/array.hpp>

using search::multivalue::Value;
using search::multivalue::WeightedValue;

namespace search::attribute {

template class MultiValueMapping<Value<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueMapping<WeightedValue<vespalib::datastore::AtomicEntryRef>>;
template class MultiValueMapping<Value<int8_t>>;
template class MultiValueMapping<WeightedValue<int8_t>>;
template class MultiValueMapping<Value<int16_t>>;
template class MultiValueMapping<WeightedValue<int16_t>>;
template class MultiValueMapping<Value<int32_t>>;
template class MultiValueMapping<WeightedValue<int32_t>>;
template class MultiValueMapping<Value<int64_t>>;
template class MultiValueMapping<WeightedValue<int64_t>>;
template class MultiValueMapping<Value<float>>;
template class MultiValueMapping<WeightedValue<float>>;
template class MultiValueMapping<Value<double>>;
template class MultiValueMapping<WeightedValue<double>>;

}
