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
    UniqueStoreRemapper(EntryRefFilter&& filter)
        : _filter(std::move(filter)),
          _mapping()
    {
    }
    virtual ~UniqueStoreRemapper() = default;

    EntryRef remap(EntryRef ref) const {
        RefType internal_ref(ref);
        auto &inner_mapping = _mapping[internal_ref.bufferId()];
        assert(internal_ref.offset() < inner_mapping.size());
        EntryRef mapped_ref = inner_mapping[internal_ref.offset()];
        assert(mapped_ref.valid());
        return mapped_ref;
    }

    void remap(vespalib::ArrayRef<AtomicEntryRef> refs) const {
        for (auto &atomic_ref : refs) {
            auto ref = atomic_ref.load_relaxed();
            if (ref.valid() && _filter.has(ref)) {
                atomic_ref.store_release(remap(ref));
            }
        }
    }

    const EntryRefFilter& get_entry_ref_filter() const noexcept { return _filter; }

    virtual void done() = 0;
};

}
