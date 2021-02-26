// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_store.h"
#include "datastore.hpp"
#include <atomic>
#include <algorithm>

namespace vespalib::datastore {

template <typename EntryT, typename RefT>
ArrayStore<EntryT, RefT>::LargeArrayType::LargeArrayType(const AllocSpec &spec)
    : BufferType<LargeArray>(1, spec.minArraysInBuffer, spec.maxArraysInBuffer, spec.numArraysForNewBuffer, spec.allocGrowFactor)
{
}

template <typename EntryT, typename RefT>
void
ArrayStore<EntryT, RefT>::LargeArrayType::cleanHold(void *buffer, size_t offset, ElemCount numElems, CleanContext cleanCtx)
{
    LargeArray *elem = static_cast<LargeArray *>(buffer) + offset;
    for (size_t i = 0; i < numElems; ++i) {
        cleanCtx.extraBytesCleaned(sizeof(EntryT) * elem->size());
        *elem = _emptyEntry;
        ++elem;
    }
}

template <typename EntryT, typename RefT>
void
ArrayStore<EntryT, RefT>::initArrayTypes(const ArrayStoreConfig &cfg)
{
    _largeArrayTypeId = _store.addType(&_largeArrayType);
    assert(_largeArrayTypeId == 0);
    _smallArrayTypes.reserve(_maxSmallArraySize);
    for (uint32_t arraySize = 1; arraySize <= _maxSmallArraySize; ++arraySize) {
        const AllocSpec &spec = cfg.specForSize(arraySize);
        _smallArrayTypes.emplace_back(arraySize, spec.minArraysInBuffer, spec.maxArraysInBuffer,
                                      spec.numArraysForNewBuffer, spec.allocGrowFactor);
    }
    for (auto & type : _smallArrayTypes) {
        uint32_t typeId = _store.addType(&type);
        assert(typeId == type.getArraySize()); // Enforce 1-to-1 mapping between type ids and sizes for small arrays
    }
}

template <typename EntryT, typename RefT>
ArrayStore<EntryT, RefT>::ArrayStore(const ArrayStoreConfig &cfg)
    : _largeArrayTypeId(0),
      _maxSmallArraySize(cfg.maxSmallArraySize()),
      _store(),
      _smallArrayTypes(),
      _largeArrayType(cfg.specForSize(0))
{
    initArrayTypes(cfg);
    _store.init_primary_buffers();
    if (cfg.enable_free_lists()) {
        _store.enableFreeLists();
    }
}

template <typename EntryT, typename RefT>
ArrayStore<EntryT, RefT>::~ArrayStore()
{
    _store.clearHoldLists();
    _store.dropBuffers();
}

template <typename EntryT, typename RefT>
EntryRef
ArrayStore<EntryT, RefT>::add(const ConstArrayRef &array)
{
    if (array.size() == 0) {
        return EntryRef();
    }
    if (array.size() <= _maxSmallArraySize) {
        return addSmallArray(array);
    } else {
        return addLargeArray(array);
    }
}

template <typename EntryT, typename RefT>
EntryRef
ArrayStore<EntryT, RefT>::addSmallArray(const ConstArrayRef &array)
{
    uint32_t typeId = getTypeId(array.size());
    using NoOpReclaimer = DefaultReclaimer<EntryT>;
    return _store.template freeListAllocator<EntryT, NoOpReclaimer>(typeId).allocArray(array).ref;
}

template <typename EntryT, typename RefT>
EntryRef
ArrayStore<EntryT, RefT>::addLargeArray(const ConstArrayRef &array)
{
    using NoOpReclaimer = DefaultReclaimer<LargeArray>;
    auto handle = _store.template freeListAllocator<LargeArray, NoOpReclaimer>(_largeArrayTypeId)
            .alloc(array.cbegin(), array.cend());
    auto& state = _store.getBufferState(RefT(handle.ref).bufferId());
    state.incExtraUsedBytes(sizeof(EntryT) * array.size());
    return handle.ref;
}

template <typename EntryT, typename RefT>
void
ArrayStore<EntryT, RefT>::remove(EntryRef ref)
{
    if (ref.valid()) {
        RefT internalRef(ref);
        uint32_t typeId = _store.getTypeId(internalRef.bufferId());
        if (typeId != _largeArrayTypeId) {
            size_t arraySize = getArraySize(typeId);
            _store.holdElem(ref, arraySize);
        } else {
            _store.holdElem(ref, 1, sizeof(EntryT) * get(ref).size());
        }
    }
}

namespace arraystore {

template <typename EntryT, typename RefT>
class CompactionContext : public ICompactionContext {
private:
    using ArrayStoreType = ArrayStore<EntryT, RefT>;
    DataStoreBase &_dataStore;
    ArrayStoreType &_store;
    std::vector<uint32_t> _bufferIdsToCompact;

    bool compactingBuffer(uint32_t bufferId) {
        return std::find(_bufferIdsToCompact.begin(), _bufferIdsToCompact.end(),
                         bufferId) != _bufferIdsToCompact.end();
    }
public:
    CompactionContext(DataStoreBase &dataStore,
                      ArrayStoreType &store,
                      std::vector<uint32_t> bufferIdsToCompact)
        : _dataStore(dataStore),
          _store(store),
          _bufferIdsToCompact(std::move(bufferIdsToCompact))
    {}
    ~CompactionContext() override {
        _dataStore.finishCompact(_bufferIdsToCompact);
    }
    void compact(vespalib::ArrayRef<EntryRef> refs) override {
        if (!_bufferIdsToCompact.empty()) {
            for (auto &ref : refs) {
                if (ref.valid()) {
                    RefT internalRef(ref);
                    if (compactingBuffer(internalRef.bufferId())) {
                        EntryRef newRef = _store.add(_store.get(ref));
                        std::atomic_thread_fence(std::memory_order_release);
                        ref = newRef;
                    }
                }
            }
        }
    }
};

}

template <typename EntryT, typename RefT>
ICompactionContext::UP
ArrayStore<EntryT, RefT>::compactWorst(bool compactMemory, bool compactAddressSpace)
{
    std::vector<uint32_t> bufferIdsToCompact = _store.startCompactWorstBuffers(compactMemory, compactAddressSpace);
    return std::make_unique<arraystore::CompactionContext<EntryT, RefT>>
        (_store, *this, std::move(bufferIdsToCompact));
}

template <typename EntryT, typename RefT>
vespalib::AddressSpace
ArrayStore<EntryT, RefT>::addressSpaceUsage() const
{
    return _store.getAddressSpaceUsage();
}

template <typename EntryT, typename RefT>
const BufferState &
ArrayStore<EntryT, RefT>::bufferState(EntryRef ref) const
{
    RefT internalRef(ref);
    return _store.getBufferState(internalRef.bufferId());
}

template <typename EntryT, typename RefT>
ArrayStoreConfig
ArrayStore<EntryT, RefT>::optimizedConfigForHugePage(size_t maxSmallArraySize,
                                                     size_t hugePageSize,
                                                     size_t smallPageSize,
                                                     size_t minNumArraysForNewBuffer,
                                                     float allocGrowFactor)
{
    return ArrayStoreConfig::optimizeForHugePage(maxSmallArraySize,
                                                 hugePageSize,
                                                 smallPageSize,
                                                 sizeof(EntryT),
                                                 RefT::offsetSize(),
                                                 minNumArraysForNewBuffer,
                                                 allocGrowFactor);
}

}
