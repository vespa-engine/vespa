// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"
#include "entry_ref_filter.h"
#include <vector>
#include <vespa/vespalib/stllike/allocator.h>

namespace vespalib::datastore {

/**
 * Remapper for related UniqueStore class, used for adjusting
 * references to unique store after compaction.
 */
template <typename RefT>
class UniqueStoreRemapper {
public:
    using RefType = RefT;

protected:
    EntryRefFilter _filter;
    std::vector<std::vector<EntryRef, allocator_large<EntryRef>>> _mapping;
public:
    UniqueStoreRemapper(EntryRefFilter&& filter);
    virtual ~UniqueStoreRemapper();

    EntryRef remap(EntryRef ref) const;
    void remap(vespalib::ArrayRef<AtomicEntryRef> refs) const;
    const EntryRefFilter& get_entry_ref_filter() const noexcept { return _filter; }
    virtual void done() = 0;
};

}
