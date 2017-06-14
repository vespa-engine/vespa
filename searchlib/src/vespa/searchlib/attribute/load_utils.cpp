// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "load_utils.hpp"
#include "multivalue.h"
#include "enumstorebase.h"
#include "loadedenumvalue.h"
#include "multi_value_mapping.h"
#include <vespa/vespalib/util/array.hpp>

using search::multivalue::Value;
using search::multivalue::WeightedValue;

namespace search {
namespace attribute {

#define INSTANTIATE_ARRAY(ValueType, Saver) \
template uint32_t loadFromEnumeratedMultiValue(MultiValueMapping<Value<ValueType>> &, ReaderBase &, vespalib::ConstArrayRef<ValueType>, Saver)
#define INSTANTIATE_WSET(ValueType, Saver) \
template uint32_t loadFromEnumeratedMultiValue(MultiValueMapping<WeightedValue<ValueType>> &, ReaderBase &, vespalib::ConstArrayRef<ValueType>, Saver)
#define INSTANTIATE_SINGLE(ValueType, Saver) \
template void loadFromEnumeratedSingleValue(RcuVectorBase<ValueType> &, vespalib::GenerationHolder &, ReaderBase &, vespalib::ConstArrayRef<ValueType>, Saver)

#define INSTANTIATE_SINGLE_ARRAY_WSET(ValueType, Saver) \
INSTANTIATE_SINGLE(ValueType, Saver); \
INSTANTIATE_ARRAY(ValueType, Saver); \
INSTANTIATE_WSET(ValueType, Saver)

#define INSTANTIATE_ENUM(Saver) \
INSTANTIATE_SINGLE_ARRAY_WSET(EnumStoreIndex, Saver)

#define INSTANTIATE_VALUE(ValueType) \
INSTANTIATE_SINGLE_ARRAY_WSET(ValueType, NoSaveLoadedEnum)

INSTANTIATE_ENUM(SaveLoadedEnum); // posting lists
INSTANTIATE_ENUM(SaveEnumHist);   // no posting lists but still enumerated
INSTANTIATE_VALUE(int8_t);
INSTANTIATE_VALUE(int16_t);
INSTANTIATE_VALUE(int32_t);
INSTANTIATE_VALUE(int64_t);
INSTANTIATE_VALUE(float);
INSTANTIATE_VALUE(double);

} // namespace search::attribute
} // namespace search
