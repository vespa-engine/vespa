// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "raw_multi_value_read_view.h"

namespace search::attribute {

template <typename MultiValueType>
RawMultiValueReadView<MultiValueType>::RawMultiValueReadView(MultiValueMappingReadView<MultiValueType> mv_mapping_read_view)
    : _mv_mapping_read_view(mv_mapping_read_view)
{
}

template <typename MultiValueType>
RawMultiValueReadView<MultiValueType>::~RawMultiValueReadView() = default;

template <typename MultiValueType>
vespalib::ConstArrayRef<MultiValueType>
RawMultiValueReadView<MultiValueType>::get_values(uint32_t docid) const
{
    return _mv_mapping_read_view.get(docid);
}

template class RawMultiValueReadView<int8_t>;
template class RawMultiValueReadView<int16_t>;
template class RawMultiValueReadView<int32_t>;
template class RawMultiValueReadView<int64_t>;
template class RawMultiValueReadView<float>;
template class RawMultiValueReadView<double>;
template class RawMultiValueReadView<vespalib::datastore::AtomicEntryRef>;

using multivalue::WeightedValue;

template class RawMultiValueReadView<WeightedValue<int8_t>>;
template class RawMultiValueReadView<WeightedValue<int16_t>>;
template class RawMultiValueReadView<WeightedValue<int32_t>>;
template class RawMultiValueReadView<WeightedValue<int64_t>>;
template class RawMultiValueReadView<WeightedValue<float>>;
template class RawMultiValueReadView<WeightedValue<double>>;
template class RawMultiValueReadView<WeightedValue<vespalib::datastore::AtomicEntryRef>>;

}
