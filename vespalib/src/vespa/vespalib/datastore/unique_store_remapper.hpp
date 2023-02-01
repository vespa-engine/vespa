// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store_remapper.h"

namespace vespalib::datastore {

template <typename RefT>
UniqueStoreRemapper<RefT>::UniqueStoreRemapper(EntryRefFilter&& filter)
    : _filter(std::move(filter)),
      _mapping()
{
}
template <typename RefT>
UniqueStoreRemapper<RefT>::~UniqueStoreRemapper() = default;

template <typename RefT>
EntryRef
UniqueStoreRemapper<RefT>::remap(EntryRef ref) const {
    RefType internal_ref(ref);
    auto &inner_mapping = _mapping[internal_ref.bufferId()];
    assert(internal_ref.offset() < inner_mapping.size());
    EntryRef mapped_ref = inner_mapping[internal_ref.offset()];
    assert(mapped_ref.valid());
    return mapped_ref;
}

template <typename RefT>
void
UniqueStoreRemapper<RefT>::remap(vespalib::ArrayRef<AtomicEntryRef> refs) const {
    for (auto &atomic_ref : refs) {
        auto ref = atomic_ref.load_relaxed();
        if (ref.valid() && _filter.has(ref)) {
            atomic_ref.store_release(remap(ref));
        }
    }
}

}
