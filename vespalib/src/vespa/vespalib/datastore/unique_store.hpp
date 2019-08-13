// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store.h"
#include "unique_store_dictionary.h"
#include "datastore.hpp"
#include <vespa/vespalib/util/bufferwriter.h>
#include "unique_store_builder.hpp"
#include "unique_store_saver.hpp"
#include <atomic>
#include <algorithm>

namespace search::datastore {

constexpr size_t NUM_ARRAYS_FOR_NEW_UNIQUESTORE_BUFFER = 1024u;
constexpr float ALLOC_GROW_FACTOR = 0.2;

template <typename EntryT, typename RefT>
UniqueStore<EntryT, RefT>::UniqueStore()
    : ICompactable(),
      _store(),
      _typeHandler(1, 2u, RefT::offsetSize(), NUM_ARRAYS_FOR_NEW_UNIQUESTORE_BUFFER, ALLOC_GROW_FACTOR),
      _typeId(0),
      _dict(std::make_unique<UniqueStoreDictionary>())
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
EntryRef
UniqueStore<EntryT, RefT>::allocate(const EntryType &value)
{
    return _store.template allocator<WrappedEntryType>(_typeId).alloc(value).ref;
}

template <typename EntryT, typename RefT>
void
UniqueStore<EntryT, RefT>::hold(EntryRef ref)
{
    _store.holdElem(ref, 1);
}

template <typename EntryT, typename RefT>
UniqueStoreAddResult
UniqueStore<EntryT, RefT>::add(const EntryType &value)
{
    Compare comp(_store, value);
    UniqueStoreAddResult result = _dict->add(comp, [this, &value]() -> EntryRef { return allocate(value); });
    getWrapped(result.ref()).inc_ref_count();
    return result;
}

template <typename EntryT, typename RefT>
EntryRef
UniqueStore<EntryT, RefT>::find(const EntryType &value)
{
    Compare comp(_store, value);
    return _dict->find(comp);
}

template <typename EntryT, typename RefT>
EntryRef
UniqueStore<EntryT, RefT>::move(EntryRef ref)
{
    return _store.template allocator<WrappedEntryType>(_typeId).alloc(getWrapped(ref)).ref;
}

template <typename EntryT, typename RefT>
void
UniqueStore<EntryT, RefT>::remove(EntryRef ref)
{
    auto &wrapped_entry = getWrapped(ref);
    if (wrapped_entry.get_ref_count() > 1u) {
        wrapped_entry.dec_ref_count();
    } else {
        EntryType unused{};
        Compare comp(_store, unused);
        if (_dict->remove(comp, ref)) {
            hold(ref);
        }
    }
}

namespace uniquestore {

template <typename RefT>
class CompactionContext : public ICompactionContext, public ICompactable {
private:
    using DictionaryTraits = btree::BTreeTraits<32, 32, 7, true>;
    using Dictionary = btree::BTree<EntryRef, uint32_t,
                                    btree::NoAggregated,
                                    EntryComparatorWrapper,
                                    DictionaryTraits>;
    DataStoreBase &_dataStore;
    UniqueStoreDictionaryBase &_dict;
    ICompactable &_store;
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

    EntryRef move(EntryRef oldRef) override {
        RefT iRef(oldRef);
        assert(iRef.valid());
        if (compactingBuffer(iRef.bufferId())) {
            assert(iRef.offset() < _mapping[iRef.bufferId()].size());
            EntryRef &mappedRef = _mapping[iRef.bufferId()][iRef.offset()];
            assert(!mappedRef.valid());
            EntryRef newRef = _store.move(oldRef);
            mappedRef = newRef;
            return newRef;
        } else {
            return oldRef;
        }
    }
    
    void fillMapping() {
        _dict.move_entries(*this);
    }

public:
    CompactionContext(DataStoreBase &dataStore,
                      UniqueStoreDictionaryBase &dict,
                      ICompactable &store,
                      std::vector<uint32_t> bufferIdsToCompact)
        : ICompactionContext(),
          ICompactable(),
          _dataStore(dataStore),
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
    return std::make_unique<uniquestore::CompactionContext<RefT>>
        (_store, *_dict, *this, std::move(bufferIdsToCompact));
}

template <typename EntryT, typename RefT>
vespalib::MemoryUsage
UniqueStore<EntryT, RefT>::getMemoryUsage() const
{
    vespalib::MemoryUsage usage = _store.getMemoryUsage();
    usage.merge(_dict->get_memory_usage());
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
    _dict->transfer_hold_lists(generation);
    _store.transferHoldLists(generation);
}

template <typename EntryT, typename RefT>
void
UniqueStore<EntryT, RefT>::trimHoldLists(generation_t firstUsed)
{
    _dict->trim_hold_lists(firstUsed);
    _store.trimHoldLists(firstUsed);
}

template <typename EntryT, typename RefT>
void
UniqueStore<EntryT, RefT>::freeze()
{
    _dict->freeze();
}

template <typename EntryT, typename RefT>
typename UniqueStore<EntryT, RefT>::Builder
UniqueStore<EntryT, RefT>::getBuilder(uint32_t uniqueValuesHint)
{
    return Builder(*this, *_dict, uniqueValuesHint);
}

template <typename EntryT, typename RefT>
typename UniqueStore<EntryT, RefT>::Saver
UniqueStore<EntryT, RefT>::getSaver() const
{
    return Saver(*_dict, _store);
}

template <typename EntryT, typename RefT>
uint32_t
UniqueStore<EntryT, RefT>::getNumUniques() const
{
    return _dict->get_num_uniques();
}

}
