// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store.h"
#include "datastore.hpp"
#include <vespa/searchlib/btree/btree.hpp>
#include <vespa/searchlib/btree/btreebuilder.hpp>
#include <vespa/searchlib/btree/btreeroot.hpp>
#include <vespa/searchlib/btree/btreenodeallocator.hpp>
#include <vespa/searchlib/btree/btreeiterator.hpp>
#include <vespa/searchlib/btree/btreenode.hpp>
#include <vespa/searchlib/util/bufferwriter.h>
#include "unique_store_builder.hpp"
#include "unique_store_saver.hpp"
#include <atomic>

namespace search::datastore {

constexpr size_t NUMCLUSTERS_FOR_NEW_UNIQUESTORE_BUFFER = 1024u;
constexpr float ALLOC_GROW_FACTOR = 0.2;

template <typename EntryT, typename RefT>
UniqueStore<EntryT, RefT>::UniqueStore()
    : _store(),
      _typeHandler(1, 2u, RefT::offsetSize(), NUMCLUSTERS_FOR_NEW_UNIQUESTORE_BUFFER, ALLOC_GROW_FACTOR),
      _typeId(0),
      _dict()
{
    _typeId = _store.addType(&_typeHandler);
    assert(_typeId == 0u);
    _store.initActiveBuffers();
}

template <typename EntryT, typename RefT>
UniqueStore<EntryT, RefT>::~UniqueStore()
{
    _store.clearHoldLists();
    _store.dropBuffers();
}

template <typename EntryT, typename RefT>
typename UniqueStore<EntryT, RefT>::AddResult
UniqueStore<EntryT, RefT>::add(const EntryType &value)
{
    Compare comp(_store, value);
    auto itr = _dict.lowerBound(RefType(), comp);
    if (itr.valid() && !comp(EntryRef(), itr.getKey())) {
        uint32_t refCount = itr.getData();
        assert(refCount != std::numeric_limits<uint32_t>::max());
        itr.writeData(refCount + 1);
        RefType iRef(itr.getKey());
        return AddResult(itr.getKey(), false);

    } else {
        EntryRef newRef = _store.template allocator<EntryType>(_typeId).alloc(value).ref;
        _dict.insert(itr, newRef, 1u);
        return AddResult(newRef, true);
    }
}

template <typename EntryT, typename RefT>
EntryRef
UniqueStore<EntryT, RefT>::find(const EntryType &value)
{
    Compare comp(_store, value);
    auto itr = _dict.lowerBound(RefType(), comp);
    if (itr.valid() && !comp(EntryRef(), itr.getKey())) {
        return itr.getKey();
    } else {
        return EntryRef();
    }
}

template <typename EntryT, typename RefT>
EntryRef
UniqueStore<EntryT, RefT>::move(EntryRef ref)
{
    return _store.template allocator<EntryType>(_typeId).alloc(get(ref)).ref;
}

template <typename EntryT, typename RefT>
void
UniqueStore<EntryT, RefT>::remove(EntryRef ref)
{
    assert(ref.valid());
    EntryType unused{};
    Compare comp(_store, unused);
    auto itr = _dict.lowerBound(ref, comp);
    if (itr.valid() && itr.getKey() == ref) {
        uint32_t refCount = itr.getData();
        if (refCount > 1) {
            itr.writeData(refCount - 1);
        } else {
            _dict.remove(itr);
            _store.holdElem(ref, 1);
        }
    }
}

namespace uniquestore {

template <typename EntryT, typename RefT>
class CompactionContext : public ICompactionContext {
private:
    using UniqueStoreType = UniqueStore<EntryT, RefT>;
    using Dictionary = typename UniqueStoreType::Dictionary;
    DataStoreBase &_dataStore;
    Dictionary &_dict;
    UniqueStoreType &_store;
    std::vector<uint32_t> _bufferIdsToCompact;
    std::vector<std::vector<EntryRef>> _mapping;

    bool compactingBuffer(uint32_t bufferId) {
        return std::find(_bufferIdsToCompact.begin(), _bufferIdsToCompact.end(),
                         bufferId) != _bufferIdsToCompact.end();
    }

    void allocMapping() {
        _mapping.resize(RefT::numBuffers());
        for (const auto bufferId : _bufferIdsToCompact) {
            BufferState &state = _dataStore.getBufferState(bufferId);
            _mapping[bufferId].resize(state.size());
        }
    }

    void fillMapping() {
        auto itr = _dict.begin();
        while (itr.valid()) {
            RefT iRef(itr.getKey());
            assert(iRef.valid());
            if (compactingBuffer(iRef.bufferId())) {
                assert(iRef.offset() < _mapping[iRef.bufferId()].size());
                EntryRef &mappedRef = _mapping[iRef.bufferId()][iRef.offset()];
                assert(!mappedRef.valid());
                EntryRef newRef = _store.move(itr.getKey());
                std::atomic_thread_fence(std::memory_order_release);
                mappedRef = newRef;
                itr.writeKey(newRef);
            }
            ++itr;
        }
    }

public:
    CompactionContext(DataStoreBase &dataStore,
                      Dictionary &dict,
                      UniqueStoreType &store,
                      std::vector<uint32_t> bufferIdsToCompact)
        : _dataStore(dataStore),
          _dict(dict),
          _store(store),
          _bufferIdsToCompact(std::move(bufferIdsToCompact)),
          _mapping()
    {
    }
    virtual ~CompactionContext() {
        _dataStore.finishCompact(_bufferIdsToCompact);
    }
    virtual void compact(vespalib::ArrayRef<EntryRef> refs) override {
        if (!_bufferIdsToCompact.empty()) {
            if (_mapping.empty()) {
                allocMapping();
                fillMapping();
            }
            for (auto &ref : refs) {
                if (ref.valid()) {
                    RefT internalRef(ref);
                    if (compactingBuffer(internalRef.bufferId())) {
                        assert(internalRef.offset() < _mapping[internalRef.bufferId()].size());
                        EntryRef newRef = _mapping[internalRef.bufferId()][internalRef.offset()];
                        assert(newRef.valid());
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
UniqueStore<EntryT, RefT>::compactWorst()
{
    std::vector<uint32_t> bufferIdsToCompact = _store.startCompactWorstBuffers(true, true);
    return std::make_unique<uniquestore::CompactionContext<EntryT, RefT>>
        (_store, _dict, *this, std::move(bufferIdsToCompact));
}

template <typename EntryT, typename RefT>
MemoryUsage
UniqueStore<EntryT, RefT>::getMemoryUsage() const
{
    MemoryUsage usage = _store.getMemoryUsage();
    usage.merge(_dict.getMemoryUsage());
    return usage;
}

template <typename EntryT, typename RefT>
const BufferState &
UniqueStore<EntryT, RefT>::bufferState(EntryRef ref) const
{
    RefT internalRef(ref);
    return _store.getBufferState(internalRef.bufferId());
}


template <typename EntryT, typename RefT>
void
UniqueStore<EntryT, RefT>::transferHoldLists(generation_t generation)
{
    _dict.getAllocator().transferHoldLists(generation);
    _store.transferHoldLists(generation);
}

template <typename EntryT, typename RefT>
void
UniqueStore<EntryT, RefT>::trimHoldLists(generation_t firstUsed)
{
    _dict.getAllocator().trimHoldLists(firstUsed);
    _store.trimHoldLists(firstUsed);
}

template <typename EntryT, typename RefT>
void
UniqueStore<EntryT, RefT>::freeze()
{
    _dict.getAllocator().freeze();
}

template <typename EntryT, typename RefT>
typename UniqueStore<EntryT, RefT>::Builder
UniqueStore<EntryT, RefT>::getBuilder(uint32_t uniqueValuesHint)
{
    return Builder(_store, _typeId, _dict, uniqueValuesHint);
}

template <typename EntryT, typename RefT>
typename UniqueStore<EntryT, RefT>::Saver
UniqueStore<EntryT, RefT>::getSaver() const
{
    return Saver(_dict, _store);
}

template <typename EntryT, typename RefT>
uint32_t
UniqueStore<EntryT, RefT>::getNumUniques() const
{
    return _dict.getFrozenView().size();
}

}
