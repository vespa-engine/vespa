// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/i_multi_value_read_view.h>

namespace search::attribute {

/**
 * Read view for the data stored in an extendable multi-value numeric
 * weighted set attribute vector (used by streaming visitor) that handles
 * optional removal of weight.
 * @tparam MultiValueType The multi-value type of the data to access.
 * @tparam BaseType The base type of the raw data to access.
 */
template <typename MultiValueType, typename BaseType>
class ExtendableNumericWeightedSetMultiValueReadView : public attribute::IMultiValueReadView<MultiValueType>
{
    const std::vector<BaseType>&        _data;
    const std::vector<uint32_t>&        _idx;
    const std::vector<int32_t>&         _weights;
    mutable std::vector<MultiValueType> _copy;
public:
    ExtendableNumericWeightedSetMultiValueReadView(const std::vector<BaseType>& data, const std::vector<uint32_t>& idx, const std::vector<int32_t>& weights);
    ~ExtendableNumericWeightedSetMultiValueReadView() override;
    vespalib::ConstArrayRef<MultiValueType> get_values(uint32_t doc_id) const override;
};

}
