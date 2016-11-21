// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"
#include "bufferstate.h"
#include "datastore.h"
#include "entryref.h"
#include "i_compaction_context.h"
#include <vespa/searchlib/common/address_space.h>
#include <vespa/vespalib/util/array.h>
#include <unordered_map>

namespace search {
namespace datastore {

/**
 * Datastore for storing arrays of type EntryT that is accessed via a 32-bit EntryRef.
 *
 * The default EntryRef type uses 17 bits for offset (131072 values) and 15 bits for buffer id (32768 buffers).
 * Arrays of size [1,maxSmallArraySize] are stored in buffers with arrays of equal size.
 * Arrays of size >maxSmallArraySize are stored in buffers with vespalib::Array instances that are heap allocated.
 *
 * The max value of maxSmallArraySize is (2^bufferBits - 1).
 */
template <typename EntryT, typename RefT = EntryRefT<17> >
class ArrayStore
{
public:
    using ConstArrayRef = vespalib::ConstArrayRef<EntryT>;
    using DataStoreType  = DataStoreT<RefT>;
    using SmallArrayType = BufferType<EntryT>;
    using LargeArray = vespalib::Array<EntryT>;

private:
    class LargeArrayType : public BufferType<LargeArray> {
    private:
        using ParentType = BufferType<LargeArray>;
        using ParentType::_emptyEntry;
        using CleanContext = typename ParentType::CleanContext;
    public:
        LargeArrayType();
        virtual void cleanHold(void *buffer, uint64_t offset, uint64_t len, CleanContext cleanCtx) override;
    };


    DataStoreType _store;
    uint32_t _maxSmallArraySize;
    std::vector<std::unique_ptr<SmallArrayType>> _smallArrayTypes;
    LargeArrayType _largeArrayType;
    uint32_t _largeArrayTypeId;
    using generation_t = vespalib::GenerationHandler::generation_t;

    void initArrayTypes();
    // 1-to-1 mapping between type ids and sizes for small arrays is enforced during initialization.
    uint32_t getTypeId(size_t arraySize) const { return arraySize; }
    size_t getArraySize(uint32_t typeId) const { return typeId; }
    EntryRef addSmallArray(const ConstArrayRef &array);
    EntryRef addLargeArray(const ConstArrayRef &array);
    ConstArrayRef getSmallArray(RefT ref, size_t arraySize) const;
    ConstArrayRef getLargeArray(RefT ref) const;

public:
    ArrayStore(uint32_t maxSmallArraySize);
    ~ArrayStore();
    EntryRef add(const ConstArrayRef &array);
    ConstArrayRef get(EntryRef ref) const;
    void remove(EntryRef ref);
    ICompactionContext::UP compactWorst();
    MemoryUsage getMemoryUsage() const { return _store.getMemoryUsage(); }

    /**
     * Returns the address space usage by this store as the ratio between active buffers
     * and the total number available buffers.
     */
    AddressSpace addressSpaceUsage() const;

    // Pass on hold list management to underlying store
    void transferHoldLists(generation_t generation) { _store.transferHoldLists(generation); }
    void trimHoldLists(generation_t firstUsed) { _store.trimHoldLists(firstUsed); }
    vespalib::GenerationHolder &getGenerationHolder(void) { return _store.getGenerationHolder(); }

    // Should only be used for unit testing
    const BufferState &bufferState(EntryRef ref) const;
};

}
}
