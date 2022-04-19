// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_value_mapping_read_view.h"
#include "enumstore.h"
#include <vespa/searchcommon/attribute/i_multi_value_read_view.h>
#include <vespa/searchcommon/attribute/multi_value_traits.h>

namespace search::attribute {

/**
 * Read view for the data stored in a multi-value attribute that handles
 * addition and removal of weight.
 * @tparam MultiValueType The multi-value type of the data to access.
 * @tparam RawMultiValueType The multi-value type of the raw data to access.
 */
template <typename MultiValueType, typename RawMultiValueType>
class CopyMultiValueReadView : public IMultiValueReadView<MultiValueType>
{
    static_assert(std::is_same_v<multivalue::ValueType_t<MultiValueType>, multivalue::ValueType_t<RawMultiValueType>>);
    using ValueType = multivalue::ValueType_t<MultiValueType>;
    MultiValueMappingReadView<RawMultiValueType> _mv_mapping_read_view;
    mutable std::vector<MultiValueType>          _copy;
public:
    CopyMultiValueReadView(MultiValueMappingReadView<RawMultiValueType> mv_mapping_read_view);
    ~CopyMultiValueReadView() override;
    vespalib::ConstArrayRef<MultiValueType> get_values(uint32_t docid) const override;
};

}
