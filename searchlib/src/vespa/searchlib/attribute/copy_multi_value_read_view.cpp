// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "copy_multi_value_read_view.h"

using vespalib::datastore::AtomicEntryRef;

namespace search::attribute {

template <typename MultiValueType, typename RawMultiValueType>
CopyMultiValueReadView<MultiValueType, RawMultiValueType>::CopyMultiValueReadView(MultiValueMappingReadView<RawMultiValueType> mv_mapping_read_view)
    : _mv_mapping_read_view(mv_mapping_read_view),
      _copy()
{
}

template <typename MultiValueType, typename RawMultiValueType>
CopyMultiValueReadView<MultiValueType, RawMultiValueType>::~CopyMultiValueReadView() = default;

template <typename MultiValueType, typename RawMultiValueType>
vespalib::ConstArrayRef<MultiValueType>
CopyMultiValueReadView<MultiValueType, RawMultiValueType>::get_values(uint32_t docid) const
{
    auto raw = _mv_mapping_read_view.get(docid);
    if (_copy.size() < raw.size()) {
        _copy.resize(raw.size());
    }
    auto dst = _copy.data();
    for (auto &src : raw) {
        ValueType v = multivalue::get_value_ref(src);
        *dst = multivalue::ValueBuilder<MultiValueType>::build(v, multivalue::get_weight(src));
        ++dst;
    }
    return vespalib::ConstArrayRef(_copy.data(), raw.size());
}

using multivalue::WeightedValue;

template class CopyMultiValueReadView<int8_t, WeightedValue<int8_t>>;
template class CopyMultiValueReadView<int16_t, WeightedValue<int16_t>>;
template class CopyMultiValueReadView<int32_t, WeightedValue<int32_t>>;
template class CopyMultiValueReadView<int64_t, WeightedValue<int64_t>>;
template class CopyMultiValueReadView<float, WeightedValue<float>>;
template class CopyMultiValueReadView<double, WeightedValue<double>>;
template class CopyMultiValueReadView<AtomicEntryRef, WeightedValue<AtomicEntryRef>>;

template class CopyMultiValueReadView<WeightedValue<int8_t>, int8_t>;
template class CopyMultiValueReadView<WeightedValue<int16_t>, int16_t>;
template class CopyMultiValueReadView<WeightedValue<int32_t>, int32_t>;
template class CopyMultiValueReadView<WeightedValue<int64_t>, int64_t>;
template class CopyMultiValueReadView<WeightedValue<float>, float>;
template class CopyMultiValueReadView<WeightedValue<double>, double>;
template class CopyMultiValueReadView<WeightedValue<AtomicEntryRef>, AtomicEntryRef>;

}
