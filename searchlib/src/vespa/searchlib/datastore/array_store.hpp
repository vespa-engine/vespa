// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "array_store.h"
#include "datastore.hpp"
#include <atomic>
#include <algorithm>

namespace search::datastore {

constexpr size_t MIN_BUFFER_CLUSTERS = 8192;

template <typename EntryT, typename RefT>
ArrayStore<EntryT, RefT>::LargeArrayType::LargeArrayType(const AllocSpec &spec)
    : BufferType<LargeArray>(1, spec.minArraysInBuffer, spec.maxArraysInBuffer, spec.numArraysForNewBuffer, spec.allocGrowFactor)
{
}

template <typename EntryT, typename RefT>
void
ArrayStore<EntryT, RefT>::LargeArrayType::cleanHold(void *buffer, uint64_t offset, uint64_t len, CleanContext cleanCtx)
{
    LargeArray *elem = static_cast<LargeArray *>(buffer) + offset;
    for (size_t i = 0; i < len; ++i) {
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
    for (uint32_t arraySize = 1; arraySize <= _maxSmallArraySize; ++arraySize) {
        const AllocSpec &spec = cfg.specForSize(arraySize);
        _smallArrayTypes.push_back(std::make_unique<SmallArrayType>
                                           (arraySize, spec.minArraysInBuffer, spec.maxArraysInBuffer,
                                            spec.numArraysForNewBuffer, spec.allocGrowFactor));
        uint32_t typeId = _store.addType(_smallArrayTypes.back().get());
        assert(typeId == arraySize); // Enforce 1-to-1 mapping between type ids and sizes for small arrays
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
    _store.initActiveBuffers();
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
    return _store.template allocator<EntryT>(typeId).allocArray(array).ref;
}

template <typename EntryT, typename RefT>
EntryRef
ArrayStore<EntryT, RefT>::addLargeArray(const ConstArrayRef &array)
{
    _store.ensureBufferCapacity(_largeArrayTypeId, 1);
    uint32_t activeBufferId = _store.getActiveBufferId(_largeArrayTypeId);
    BufferState &state = _store.getBufferState(activeBufferId);
    assert(state.isActive());
    size_t oldBufferSize = state.size();
    LargeArray *buf = _store.template getBufferEntry<LargeArray>(activeBufferId, oldBufferSize);
    new (static_cast<void *>(buf)) LargeArray(array.cbegin(), array.cend());
    state.pushed_back(1, sizeof(EntryT) * array.size());
    return RefT(oldBufferSize, activeBufferId);
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
    virtual ~CompactionContext() {
        _dataStore.finishCompact(_bufferIdsToCompact);
    }
    virtual void compact(vespalib::ArrayRef<EntryRef> refs) override {
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
AddressSpace
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

