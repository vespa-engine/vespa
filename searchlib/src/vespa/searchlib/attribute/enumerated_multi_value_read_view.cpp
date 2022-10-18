// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "enumerated_multi_value_read_view.h"

using vespalib::datastore::AtomicEntryRef;

namespace search::attribute {

template <typename MultiValueType, typename RawMultiValueType, typename EnumEntryType>
EnumeratedMultiValueReadView<MultiValueType, RawMultiValueType, EnumEntryType>::EnumeratedMultiValueReadView(MultiValueMappingReadView<RawMultiValueType> mv_mapping_read_view, const EnumStoreT<EnumEntryType>& enum_store)
    : _mv_mapping_read_view(mv_mapping_read_view),
      _enum_store(enum_store),
      _copy()
{
}

template <typename MultiValueType, typename RawMultiValueType, typename EnumEntryType>
EnumeratedMultiValueReadView<MultiValueType, RawMultiValueType, EnumEntryType>::~EnumeratedMultiValueReadView() = default;

template <typename MultiValueType, typename RawMultiValueType, typename EnumEntryType>
vespalib::ConstArrayRef<MultiValueType>
EnumeratedMultiValueReadView<MultiValueType, RawMultiValueType, EnumEntryType>::get_values(uint32_t docid) const
{
    auto raw = _mv_mapping_read_view.get(docid);
    if (_copy.size() < raw.size()) [[unlikely]] {
        _copy.resize(raw.size());
    }
    auto dst = _copy.data();
    for (auto &src : raw) {
        EnumEntryType v = _enum_store.get_value(multivalue::get_value_ref(src).load_acquire());
        *dst = multivalue::ValueBuilder<MultiValueType>::build(v, multivalue::get_weight(src));
        ++dst;
    }
    return vespalib::ConstArrayRef(_copy.data(), raw.size());
}

using multivalue::WeightedValue;

using WeightedAtomicEntryRef = WeightedValue<AtomicEntryRef>;

template class EnumeratedMultiValueReadView<int8_t, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<int16_t, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<int32_t, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<int64_t, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<float, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<double, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<const char*, AtomicEntryRef>;

template class EnumeratedMultiValueReadView<int8_t, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<int16_t, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<int32_t, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<int64_t, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<float, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<double, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<const char*, WeightedAtomicEntryRef>;

template class EnumeratedMultiValueReadView<WeightedValue<int8_t>, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<int16_t>, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<int32_t>, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<int64_t>, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<float>, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<double>, WeightedAtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<const char*>, WeightedAtomicEntryRef>;

template class EnumeratedMultiValueReadView<WeightedValue<int8_t>, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<int16_t>, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<int32_t>, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<int64_t>, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<float>, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<double>, AtomicEntryRef>;
template class EnumeratedMultiValueReadView<WeightedValue<const char*>, AtomicEntryRef>;

}
