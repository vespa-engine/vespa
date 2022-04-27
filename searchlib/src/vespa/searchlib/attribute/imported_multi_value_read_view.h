// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/i_multi_value_read_view.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>

namespace search::attribute {

/**
 * Multi value read view adapter for imported atributes vectors.
 * Performs lid mapping.
 * @tparam MultiValueType The multi-value type of the data to access.
 */
template <typename MultiValueType>
class ImportedMultiValueReadView : public IMultiValueReadView<MultiValueType>
{
    using AtomicTargetLid = vespalib::datastore::AtomicValueWrapper<uint32_t>;
    using TargetLids = vespalib::ConstArrayRef<AtomicTargetLid>;
    TargetLids                                 _target_lids;
    const IMultiValueReadView<MultiValueType>* _target_read_view;

    uint32_t get_target_lid(uint32_t lid) const {
        // Check range to avoid reading memory beyond end of mapping array
        return lid < _target_lids.size() ? _target_lids[lid].load_acquire() : 0u;
    }
public:
    ImportedMultiValueReadView(TargetLids target_lids, const IMultiValueReadView<MultiValueType>* target_read_view);
    ~ImportedMultiValueReadView() override;
    vespalib::ConstArrayRef<MultiValueType> get_values(uint32_t docid) const override;
};

}
