// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "imported_multi_value_read_view.h"

using vespalib::datastore::AtomicEntryRef;

namespace search::attribute {

template <typename MultiValueType>
ImportedMultiValueReadView<MultiValueType>::ImportedMultiValueReadView(TargetLids target_lids, const IMultiValueReadView<MultiValueType>* target_read_view)
    : _target_lids(target_lids),
      _target_read_view(target_read_view)
{
}

template <typename MultiValueType>
ImportedMultiValueReadView<MultiValueType>::~ImportedMultiValueReadView() = default;

template <typename MultiValueType>
vespalib::ConstArrayRef<MultiValueType>
ImportedMultiValueReadView<MultiValueType>::get_values(uint32_t docid) const
{
    auto target_lid = get_target_lid(docid);
    return _target_read_view->get_values(target_lid);
}

using multivalue::WeightedValue;

template class ImportedMultiValueReadView<int8_t>;
template class ImportedMultiValueReadView<int16_t>;
template class ImportedMultiValueReadView<int32_t>;
template class ImportedMultiValueReadView<int64_t>;
template class ImportedMultiValueReadView<float>;
template class ImportedMultiValueReadView<double>;
template class ImportedMultiValueReadView<AtomicEntryRef>;
template class ImportedMultiValueReadView<const char*>;

template class ImportedMultiValueReadView<WeightedValue<int8_t>>;
template class ImportedMultiValueReadView<WeightedValue<int16_t>>;
template class ImportedMultiValueReadView<WeightedValue<int32_t>>;
template class ImportedMultiValueReadView<WeightedValue<int64_t>>;
template class ImportedMultiValueReadView<WeightedValue<float>>;
template class ImportedMultiValueReadView<WeightedValue<double>>;
template class ImportedMultiValueReadView<WeightedValue<AtomicEntryRef>>;
template class ImportedMultiValueReadView<WeightedValue<const char*>>;

}
