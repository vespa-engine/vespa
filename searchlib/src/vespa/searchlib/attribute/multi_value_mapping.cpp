// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attributevector.h"
#include "i_enum_store.h"
#include "multi_value_mapping.h"
#include "multi_value_mapping.hpp"
#include "multivalue.h"
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/datastore/buffer_type.hpp>

using search::multivalue::Value;
using search::multivalue::WeightedValue;

namespace search::attribute {

template class MultiValueMapping<Value<IEnumStore::Index>>;
template class MultiValueMapping<WeightedValue<IEnumStore::Index>>;
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
