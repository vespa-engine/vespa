// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_compactable.h"

namespace vespalib::datastore {

/**
 * Extension of interface for moving an entry as part of compaction of
 * data in old buffers into new buffers where move is only supposed to
 * be called for entries referencing a buffer being compacted.
 */
struct IFilteredCompactable : public ICompactable {
    virtual ~IFilteredCompactable() = default;
    virtual uint32_t get_entry_ref_offset_bits() const = 0;
    virtual std::vector<bool> get_compacting_buffers() const = 0;
};

}
