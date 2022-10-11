// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "datastore.h"
#include "entryref.h"
#include "unique_store_add_result.h"
#include "unique_store_buffer_type.h"
#include "unique_store_entry.h"
#include "i_compactable.h"

namespace vespalib::alloc { class MemoryAllocator; }

namespace vespalib::datastore {

/**
 * Allocator for unique values of type EntryT that is accessed via a
 * 32-bit EntryRef.
 */
template <typename EntryT, typename RefT = EntryRefT<22> >
class UniqueStoreAllocator : public ICompactable
{
public:
    using DataStoreType = DataStoreT<RefT>;
    using EntryType = EntryT;
    using EntryConstRefType = const EntryType &;
    using WrappedEntryType = UniqueStoreEntry<EntryType>;
    using RefType = RefT;
private:
    DataStoreType _store;
    UniqueStoreBufferType<WrappedEntryType> _typeHandler;

public:
    UniqueStoreAllocator(std::shared_ptr<alloc::MemoryAllocator> memory_allocator);
    ~UniqueStoreAllocator() override;
    EntryRef allocate(const EntryType& value);
    void hold(EntryRef ref);
    EntryRef move_on_compact(EntryRef ref) override;
    const WrappedEntryType& get_wrapped(EntryRef ref) const {
        RefType iRef(ref);
        return *_store.template getEntry<WrappedEntryType>(iRef);
    }
    const EntryType& get(EntryRef ref) const {
        return get_wrapped(ref).value();
    }
    DataStoreType& get_data_store() noexcept { return _store; }
    const DataStoreType& get_data_store() const noexcept { return _store; }
};

}
