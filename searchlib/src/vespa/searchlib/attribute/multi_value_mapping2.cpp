// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
#include "multi_value_mapping2.h"
#include "multi_value_mapping2.hpp"
#include <vespa/vespalib/stllike/string.h>
#include "multivalue.h"
#include "enumstorebase.h"

LOG_SETUP(".searchlib.attribute.multivaluemapping2");

using search::multivalue::Value;
using search::multivalue::WeightedValue;

namespace search {
namespace attribute {

MultiValueMapping2Base::MultiValueMapping2Base(const GrowStrategy &gs,
                                               vespalib::GenerationHolder &genHolder)
    : _indices(gs, genHolder)
{
}

MultiValueMapping2Base::~MultiValueMapping2Base()
{
}

template class MultiValueMapping2<Value<EnumStoreIndex>>;
template class MultiValueMapping2<WeightedValue<EnumStoreIndex>>;
template class MultiValueMapping2<Value<int8_t>>;
template class MultiValueMapping2<WeightedValue<int8_t>>;
template class MultiValueMapping2<Value<int16_t>>;
template class MultiValueMapping2<WeightedValue<int16_t>>;
template class MultiValueMapping2<Value<int32_t>>;
template class MultiValueMapping2<WeightedValue<int32_t>>;
template class MultiValueMapping2<Value<int64_t>>;
template class MultiValueMapping2<WeightedValue<int64_t>>;
template class MultiValueMapping2<Value<float>>;
template class MultiValueMapping2<WeightedValue<float>>;
template class MultiValueMapping2<Value<double>>;
template class MultiValueMapping2<WeightedValue<double>>;

} // namespace search::attribute
} // namespace search
