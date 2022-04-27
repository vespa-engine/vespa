// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "multi_value_mapping_read_view.h"
#include <vespa/searchcommon/attribute/i_multi_value_read_view.h>

namespace search::attribute {

/**
 * Read view for the raw data stored in a multi-value attribute.
 * @tparam MultiValueType The multi-value type of the raw data to access.
 */
template <typename MultiValueType>
class RawMultiValueReadView : public IMultiValueReadView<MultiValueType>
{
    MultiValueMappingReadView<MultiValueType> _mv_mapping_read_view;
public:
    RawMultiValueReadView(MultiValueMappingReadView<MultiValueType> mv_mapping_read_view);
    ~RawMultiValueReadView() override;
    vespalib::ConstArrayRef<MultiValueType> get_values(uint32_t docid) const override;
};

}
