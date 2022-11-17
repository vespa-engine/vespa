// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/atomic_value_wrapper.h>

namespace search::tensor {

/**
 * Represents a graph node for non-dense tensors (multiple nodes per document).
 */
class HnswNode {
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using EntryRef = vespalib::datastore::EntryRef;

    AtomicEntryRef _ref;
    vespalib::datastore::AtomicValueWrapper<uint32_t> _docid;
    vespalib::datastore::AtomicValueWrapper<uint32_t> _subspace;

public:
    HnswNode()
        : _ref(),
          _docid(),
          _subspace()
    {
    }
    AtomicEntryRef& ref() noexcept { return _ref; }
    const AtomicEntryRef& ref() const noexcept { return _ref; }
    void store_docid(uint32_t docid) noexcept { _docid.store_release(docid); }
    void store_subspace(uint32_t subspace) noexcept { _subspace.store_release(subspace); }
    // Mapping from nodeid to docid and subspace.
    uint32_t acquire_docid() const noexcept { return _docid.load_acquire(); }
    uint32_t acquire_subspace() const noexcept { return _subspace.load_acquire(); }
    static constexpr bool identity_mapping = false;
};

}
