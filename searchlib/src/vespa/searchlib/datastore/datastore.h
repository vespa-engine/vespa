// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "allocator.h"
#include "datastorebase.h"
#include "free_list_allocator.h"
#include "free_list_raw_allocator.h"
#include "raw_allocator.h"

namespace search::btree {

template<typename EntryType>
struct DefaultReclaimer {
    static void reclaim(EntryType *entry) {
        (void) entry;
    }
};

}

namespace search::datastore {

/**
 * Concrete data store using the given EntryRef type to reference stored data.
 */
template <typename RefT = EntryRefT<22> >
class DataStoreT : public DataStoreBase
{
private:
public:
    typedef RefT RefType;

    DataStoreT(const DataStoreT &rhs) = delete;
    DataStoreT &operator=(const DataStoreT &rhs) = delete;
    DataStoreT();
    ~DataStoreT();

    /**
     * Increase number of dead elements in buffer.
     *
     * @param ref       Reference to dead stored features
     * @param dead      Number of newly dead elements
     */
    void incDead(EntryRef ref, size_t deadElems) {
        RefType intRef(ref);
        DataStoreBase::incDead(intRef.bufferId(), deadElems);
    }

    /**
     * Free element(s).
     */
    void freeElem(EntryRef ref, size_t numElems);

    /**
     * Hold element(s).
     */
    void holdElem(EntryRef ref, size_t numElems, size_t extraBytes = 0);

    /**
     * Trim elem hold list, freeing elements that no longer needs to be held.
     *
     * @param usedGen       lowest generation that is still used.
     */
    void trimElemHoldList(generation_t usedGen) override;

    void clearElemHoldList() override;

    bool getCompacting(EntryRef ref) const {
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
    typedef DataStoreT<RefT> ParentType;
    using ParentType::ensureBufferCapacity;
    using ParentType::_activeBufferIds;
    using ParentType::_freeListLists;
    using ParentType::getBufferEntry;
    using ParentType::dropBuffers;
    using ParentType::initActiveBuffers;
    using ParentType::addType;

    BufferType<EntryType> _type;
public:
    typedef typename ParentType::RefType RefType;
    DataStore(const DataStore &rhs) = delete;
    DataStore &operator=(const DataStore &rhs) = delete;
    DataStore();
    ~DataStore();

    EntryRef addEntry(const EntryType &e);
    const EntryType &getEntry(EntryRef ref) const;

    template <typename ReclaimerT>
    FreeListAllocator<EntryType, RefT, ReclaimerT> freeListAllocator();
};

extern template class DataStoreT<EntryRefT<22> >;

}
