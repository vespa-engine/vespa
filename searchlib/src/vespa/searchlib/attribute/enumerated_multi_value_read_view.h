// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_value_mapping_read_view.h"
#include "enumstore.h"
#include <vespa/searchcommon/attribute/i_multi_value_read_view.h>
#include <vespa/searchcommon/attribute/multi_value_traits.h>

namespace search::attribute {

/**
 * Read view for the data stored in a multi-value attribute that handles
 * mapping from enumerated value to value.
 * @tparam MultiValueType The multi-value type of the data to access.
 * @tparam RawMultiValueType The multi-value type of the raw data to access.
 * @tparam EnumEntryType The enum store entry type.
 */
template <typename MultiValueType, typename RawMultiValueType, typename EnumEntryType = multivalue::ValueType_t<MultiValueType>>
class EnumeratedMultiValueReadView : public IMultiValueReadView<MultiValueType>
{
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    MultiValueMappingReadView<RawMultiValueType> _mv_mapping_read_view;
    const EnumStoreT<EnumEntryType>&             _enum_store;
    mutable std::vector<MultiValueType>          _copy;
public:
    EnumeratedMultiValueReadView(MultiValueMappingReadView<RawMultiValueType> mv_mapping_read_view, const EnumStoreT<EnumEntryType>& enum_store);
    ~EnumeratedMultiValueReadView() override;
    vespalib::ConstArrayRef<MultiValueType> get_values(uint32_t docid) const override;
};

}
