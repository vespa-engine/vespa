// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "extendable_numeric_array_multi_value_read_view.h"

namespace search::attribute {

template <class MultiValueType, typename BaseType>
ExtendableNumericArrayMultiValueReadView<MultiValueType, BaseType>::ExtendableNumericArrayMultiValueReadView(const std::vector<BaseType>& data, const std::vector<uint32_t>& idx)
    : attribute::IMultiValueReadView<MultiValueType>(),
      _data(data),
      _idx(idx),
      _copy()
{
}

template <class MultiValueType, typename BaseType>
ExtendableNumericArrayMultiValueReadView<MultiValueType, BaseType>::~ExtendableNumericArrayMultiValueReadView() = default;

template <class MultiValueType, typename BaseType>
vespalib::ConstArrayRef<MultiValueType>
ExtendableNumericArrayMultiValueReadView<MultiValueType, BaseType>::get_values(uint32_t doc_id) const
{
    auto offset = _idx[doc_id];
    auto next_offset = _idx[doc_id + 1];
    vespalib::ConstArrayRef<BaseType> raw(_data.data() + offset, next_offset - offset);
    if (_copy.size() < raw.size()) {
        _copy.resize(raw.size());
    }
    auto dst = _copy.data();
    for (auto &src : raw) {
        *dst = multivalue::ValueBuilder<MultiValueType>::build(src, 1);
        ++dst;
    }
    return vespalib::ConstArrayRef(_copy.data(), raw.size());
}

template class ExtendableNumericArrayMultiValueReadView<int8_t, int8_t>;
template class ExtendableNumericArrayMultiValueReadView<int16_t, int16_t>;
template class ExtendableNumericArrayMultiValueReadView<int32_t, int32_t>;
template class ExtendableNumericArrayMultiValueReadView<int64_t, int64_t>;
template class ExtendableNumericArrayMultiValueReadView<double, double>;
template class ExtendableNumericArrayMultiValueReadView<multivalue::WeightedValue<int8_t>, int8_t>;
template class ExtendableNumericArrayMultiValueReadView<multivalue::WeightedValue<int16_t>, int16_t>;
template class ExtendableNumericArrayMultiValueReadView<multivalue::WeightedValue<int32_t>, int32_t>;
template class ExtendableNumericArrayMultiValueReadView<multivalue::WeightedValue<int64_t>, int64_t>;
template class ExtendableNumericArrayMultiValueReadView<multivalue::WeightedValue<double>, double>;

}
