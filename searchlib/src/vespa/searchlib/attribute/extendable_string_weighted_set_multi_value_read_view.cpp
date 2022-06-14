// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "extendable_string_weighted_set_multi_value_read_view.h"

namespace search::attribute {

template <class MultiValueType>
ExtendableStringWeightedSetMultiValueReadView<MultiValueType>::ExtendableStringWeightedSetMultiValueReadView(const std::vector<char>& buffer, const Offsets & offsets, const std::vector<uint32_t>& idx, const std::vector<int32_t>& weights)
    : attribute::IMultiValueReadView<MultiValueType>(),
      _buffer(buffer),
      _offsets(offsets),
      _idx(idx),
      _weights(weights),
      _copy()
{
}

template <class MultiValueType>
ExtendableStringWeightedSetMultiValueReadView<MultiValueType>::~ExtendableStringWeightedSetMultiValueReadView() = default;

template <class MultiValueType>
vespalib::ConstArrayRef<MultiValueType>
ExtendableStringWeightedSetMultiValueReadView<MultiValueType>::get_values(uint32_t doc_id) const
{
    auto offset = _idx[doc_id];
    auto next_offset = _idx[doc_id + 1];
    vespalib::ConstArrayRef<uint32_t> raw(&_offsets[offset], next_offset - offset);
    if (_copy.size() < raw.size()) {
        _copy.resize(raw.size());
    }
    auto dst = _copy.data();
    auto* src_weight = _weights.data() + offset;
   for (auto &src : raw) {
        *dst = multivalue::ValueBuilder<MultiValueType>::build(_buffer.data() + src, *src_weight);
        ++src_weight;
        ++dst;
    }
    return vespalib::ConstArrayRef(_copy.data(), raw.size());
}

template class ExtendableStringWeightedSetMultiValueReadView<multivalue::WeightedValue<const char*>>;

}
