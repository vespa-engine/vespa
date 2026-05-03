// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcommon/attribute/i_array_bool_read_view.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>

#include <span>

namespace search::attribute {

/**
 * Array bool read view adapter for imported attribute vectors.
 * Performs lid mapping.
 */
class ImportedArrayBoolReadView : public IArrayBoolReadView {
    using AtomicTargetLid = vespalib::datastore::AtomicValueWrapper<uint32_t>;
    using TargetLids = std::span<const AtomicTargetLid>;
    TargetLids                _target_lids;
    const IArrayBoolReadView* _target_read_view;

    uint32_t get_target_lid(uint32_t lid) const {
        // Check range to avoid reading memory beyond end of mapping array
        return lid < _target_lids.size() ? _target_lids[lid].load_acquire() : 0u;
    }

public:
    ImportedArrayBoolReadView(TargetLids target_lids, const IArrayBoolReadView* target_read_view);
    ~ImportedArrayBoolReadView() override;
    BitSpan get_values(uint32_t docid) const override;
};

} // namespace search::attribute
