// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_store_config.h"
#include "buffer_type.h"
#include "bufferstate.h"
#include "datastore.h"
#include "entryref.h"
#include "i_compaction_context.h"
#include <vespa/vespalib/util/array.h>

namespace search::datastore {

/**
 * Datastore for storing arrays of type EntryT that is accessed via a 32-bit EntryRef.
 *
 * The default EntryRef type uses 19 bits for offset (524288 values) and 13 bits for buffer id (8192 buffers).
 * Arrays of size [1,maxSmallArraySize] are stored in buffers with arrays of equal size.
 * Arrays of size >maxSmallArraySize are stored in buffers with vespalib::Array instances that are heap allocated.
 *
 * The max value of maxSmallArraySize is (2^bufferBits - 1).
 */
template <typename EntryT, typename RefT = EntryRefT<19> >
class ArrayStore
{
public:
    using ConstArrayRef = vespalib::ConstArrayRef<EntryT>;
    using DataStoreType  = DataStoreT<RefT>;
    using SmallArrayType = BufferType<EntryT>;
    using LargeArray = vespalib::Array<EntryT>;
    using AllocSpec = ArrayStoreConfig::AllocSpec;

private:
    class LargeArrayType : public BufferType<LargeArray> {
    private:
        using ParentType = BufferType<LargeArray>;
        using ParentType::_emptyEntry;
        using CleanContext = typename ParentType::CleanContext;
    public:
        LargeArrayType(const AllocSpec &spec);
        virtual void cleanHold(void *buffer, uint64_t offset, uint64_t len, CleanContext cleanCtx) override;
    };


    uint32_t _largeArrayTypeId;
    uint32_t _maxSmallArraySize;
    DataStoreType _store;
    std::vector<std::unique_ptr<SmallArrayType>> _smallArrayTypes;
    LargeArrayType _largeArrayType;
    using generation_t = vespalib::GenerationHandler::generation_t;

    void initArrayTypes(const ArrayStoreConfig &cfg);
    // 1-to-1 mapping between type ids and sizes for small arrays is enforced during initialization.
    uint32_t getTypeId(size_t arraySize) const { return arraySize; }
    size_t getArraySize(uint32_t typeId) const { return typeId; }
    EntryRef addSmallArray(const ConstArrayRef &array);
    EntryRef addLargeArray(const ConstArrayRef &array);
    ConstArrayRef getSmallArray(RefT ref, size_t arraySize) const {
        size_t bufferOffset = ref.offset() * arraySize;
        const EntryT *buf = _store.template getBufferEntry<EntryT>(ref.bufferId(), bufferOffset);
        return ConstArrayRef(buf, arraySize);
    }
    ConstArrayRef getLargeArray(RefT ref) const {
        const LargeArray *buf = _store.template getBufferEntry<LargeArray>(ref.bufferId(), ref.offset());
        return ConstArrayRef(&(*buf)[0], buf->size());
    }

public:
    ArrayStore(const ArrayStoreConfig &cfg);
    ~ArrayStore();
    EntryRef add(const ConstArrayRef &array);
    ConstArrayRef get(EntryRef ref) const {
        if (!ref.valid()) {
            return ConstArrayRef();
        }
        RefT internalRef(ref);
        uint32_t typeId = _store.getTypeId(internalRef.bufferId());
        if (typeId != _largeArrayTypeId) {
            size_t arraySize = getArraySize(typeId);
            return getSmallArray(internalRef, arraySize);
        } else {
            return getLargeArray(internalRef);
        }
    }
    void remove(EntryRef ref);
    ICompactionContext::UP compactWorst(bool compactMemory, bool compactAddressSpace);
    MemoryUsage getMemoryUsage() const { return _store.getMemoryUsage(); }

    /**
     * Returns the address space usage by this store as the ratio between active buffers
     * and the total number available buffers.
     */
    AddressSpace addressSpaceUsage() const;

    // Pass on hold list management to underlying store
    void transferHoldLists(generation_t generation) { _store.transferHoldLists(generation); }
    void trimHoldLists(generation_t firstUsed) { _store.trimHoldLists(firstUsed); }
    vespalib::GenerationHolder &getGenerationHolder() { return _store.getGenerationHolder(); }
    void setInitializing(bool initializing) { _store.setInitializing(initializing); }

    // Should only be used for unit testing
    const BufferState &bufferState(EntryRef ref) const;

    static ArrayStoreConfig optimizedConfigForHugePage(size_t maxSmallArraySize,
                                                       size_t hugePageSize,
                                                       size_t smallPageSize,
                                                       size_t minNumArraysForNewBuffer,
                                                       float allocGrowFactor);
};

}
