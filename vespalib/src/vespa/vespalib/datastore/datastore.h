// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "allocator.h"
#include "datastorebase.h"
#include "free_list_allocator.h"
#include "free_list_raw_allocator.h"
#include "raw_allocator.h"

namespace vespalib::datastore {

/**
 * Default noop reclaimer used together with datastore allocators.
 */
template<typename EntryType>
struct DefaultReclaimer {
    static void reclaim(EntryType *entry) {
        (void) entry;
    }
};

/**
 * Concrete data store using the given EntryRef type to reference stored data.
 */
template <typename RefT = EntryRefT<22> >
class DataStoreT : public DataStoreBase
{
private:
    void free_entry_internal(EntryRef ref, size_t num_entries);

public:
    using RefType = RefT;

    DataStoreT(const DataStoreT &rhs) = delete;
    DataStoreT &operator=(const DataStoreT &rhs) = delete;
    DataStoreT();
    ~DataStoreT() override;

    /**
     * Hold entries.
     */
    void hold_entry(EntryRef ref) { hold_entries(ref, 1, 0); }
    void hold_entry(EntryRef ref, size_t extra_bytes) { hold_entries(ref, 1, extra_bytes); }
    void hold_entries(EntryRef ref, size_t num_entries) { hold_entries(ref, num_entries, 0); }
    void hold_entries(EntryRef ref, size_t num_entries, size_t extraBytes);

    void reclaim_entry_refs(generation_t oldest_used_gen) override;

    void reclaim_all_entry_refs() override;

    bool getCompacting(EntryRef ref) {
        return getBufferState(RefType(ref).bufferId()).getCompacting();
    }

    template <typename EntryT>
    Allocator<EntryT, RefT> allocator(uint32_t typeId);

    template <typename EntryT, typename ReclaimerT>
    FreeListAllocator<EntryT, RefT, ReclaimerT> freeListAllocator(uint32_t typeId);

    template <typename EntryT>
    RawAllocator<EntryT, RefT> rawAllocator(uint32_t typeId);

    template <typename EntryT>
    FreeListRawAllocator<EntryT, RefT> freeListRawAllocator(uint32_t typeId);

};

/**
 * Concrete data store storing elements of type EntryType, using the given EntryRef type to reference stored data.
 */
template <typename EntryType, typename RefT = EntryRefT<22> >
class DataStore : public DataStoreT<RefT>
{
protected:
    using ParentType = DataStoreT<RefT>;
    using ParentType::ensure_buffer_capacity;
    using ParentType::getEntry;
    using ParentType::dropBuffers;
    using ParentType::init_primary_buffers;
    using ParentType::addType;
    using BufferTypeUP = std::unique_ptr<BufferType<EntryType>>;

    BufferTypeUP _type;


public:
    using RefType = typename ParentType::RefType;
    DataStore(const DataStore &rhs) = delete;
    DataStore &operator=(const DataStore &rhs) = delete;
    DataStore();
    explicit DataStore(uint32_t min_arrays);
    explicit DataStore(BufferTypeUP type);
    ~DataStore();

    EntryRef addEntry(const EntryType &e);

    const EntryType &getEntry(EntryRef ref) const {
        return *this->template getEntry<EntryType>(RefType(ref));
    }
};

extern template class DataStoreT<EntryRefT<22> >;

}
