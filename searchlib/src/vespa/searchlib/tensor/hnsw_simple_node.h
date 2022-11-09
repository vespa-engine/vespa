// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/atomic_entry_ref.h>

namespace search::tensor {

/**
 * Represents a graph node for dense tensors (one node per document).
 */
class HnswSimpleNode {
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EntryRef = vespalib::datastore::EntryRef;

    AtomicEntryRef _ref;

public:
    HnswSimpleNode()
        : _ref()
    {
    }
    AtomicEntryRef& ref() noexcept { return _ref; }
    const AtomicEntryRef& ref() const noexcept { return _ref; }
    void store_docid(uint32_t docid) noexcept { (void) docid; }
    void store_subspace(uint32_t subspace) noexcept { (void) subspace; }
    // Mapping from nodeid to docid and subspace.
    static uint32_t acquire_docid() noexcept { return 0u; }
    static uint32_t acquire_subspace() noexcept { return 0u; }
    static constexpr bool identity_mapping = true;
};

}
