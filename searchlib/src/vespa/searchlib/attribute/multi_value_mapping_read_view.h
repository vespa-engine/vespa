// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/util/address_space.h>

namespace search::attribute {

/**
 * Class for mapping from document id to an array of values as reader.
 */
template <typename EntryT, typename RefT = vespalib::datastore::EntryRefT<19> >
class MultiValueMappingReadView
{
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using Indices = vespalib::ConstArrayRef<AtomicEntryRef>;
    using ArrayStore = vespalib::datastore::ArrayStore<EntryT, RefT>;

    Indices           _indices;
    const ArrayStore* _store;
public:
    constexpr MultiValueMappingReadView()
        : _indices(),
          _store(nullptr)
    {
    }
    MultiValueMappingReadView(Indices indices, const ArrayStore* store)
        : _indices(indices),
          _store(store)
    {
    }
    vespalib::ConstArrayRef<EntryT> get(uint32_t doc_id) const { return _store->get(_indices[doc_id].load_acquire()); }
    bool valid() const noexcept { return _store != nullptr; }
};

}
