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
    // Mapping from nodeid to docid and subspace.
    static uint32_t acquire_docid(uint32_t nodeid) noexcept { return nodeid; }
    static uint32_t acquire_subspace() noexcept { return 0u; }
};

}
