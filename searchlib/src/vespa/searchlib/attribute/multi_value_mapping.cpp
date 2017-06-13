// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "multi_value_mapping.h"
#include "multi_value_mapping.hpp"
#include "multivalue.h"
#include "enumstorebase.h"
#include "attributevector.h"
#include <vespa/vespalib/util/array.hpp>

using search::multivalue::Value;
using search::multivalue::WeightedValue;

namespace search {
namespace attribute {

template class MultiValueMapping<Value<EnumStoreIndex>>;
template class MultiValueMapping<WeightedValue<EnumStoreIndex>>;
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

} // namespace search::attribute
} // namespace search
