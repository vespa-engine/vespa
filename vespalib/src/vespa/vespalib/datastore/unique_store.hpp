// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store.h"
#include "unique_store_dictionary.h"
#include "datastore.hpp"
#include "unique_store_allocator.hpp"
#include "unique_store_builder.hpp"
#include "unique_store_dictionary.hpp"
#include "unique_store_enumerator.hpp"
#include <vespa/vespalib/util/bufferwriter.h>
#include <atomic>
#include <algorithm>

namespace search::datastore {

namespace uniquestore {

using DefaultDictionaryTraits = btree::BTreeTraits<32, 32, 7, true>;
using DefaultDictionary = btree::BTree<EntryRef, btree::BTreeNoLeafData,
                                       btree::NoAggregated,
                                       EntryComparatorWrapper,
                                       DefaultDictionaryTraits>;
using DefaultUniqueStoreDictionary = UniqueStoreDictionary<DefaultDictionary>;

}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
UniqueStore<EntryT, RefT, Compare, Allocator>::UniqueStore()
    : _allocator(),
      _store(_allocator.get_data_store()),
      _dict(std::make_unique<uniquestore::DefaultUniqueStoreDictionary>())
{
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
UniqueStore<EntryT, RefT, Compare, Allocator>::~UniqueStore() = default;

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
UniqueStoreAddResult
UniqueStore<EntryT, RefT, Compare, Allocator>::add(EntryConstRefType value)
{
    Compare comp(_store, value);
    UniqueStoreAddResult result = _dict->add(comp, [this, &value]() -> EntryRef { return _allocator.allocate(value); });
    _allocator.get_wrapped(result.ref()).inc_ref_count();
    return result;
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
EntryRef
UniqueStore<EntryT, RefT, Compare, Allocator>::find(EntryConstRefType value)
{
    Compare comp(_store, value);
    return _dict->find(comp);
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
void
UniqueStore<EntryT, RefT, Compare, Allocator>::remove(EntryRef ref)
{
    auto &wrapped_entry = _allocator.get_wrapped(ref);
    auto ref_count = wrapped_entry.get_ref_count();
    assert(ref_count > 0u);
    wrapped_entry.dec_ref_count();
    if (ref_count == 1u) {
        EntryType unused{};
        Compare comp(_store, unused);
        _dict->remove(comp, ref);
        _allocator.hold(ref);
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
    IUniqueStoreDictionary &_dict;
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
                      IUniqueStoreDictionary &dict,
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

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
ICompactionContext::UP
UniqueStore<EntryT, RefT, Compare, Allocator>::compactWorst()
{
    std::vector<uint32_t> bufferIdsToCompact = _store.startCompactWorstBuffers(true, true);
    return std::make_unique<uniquestore::CompactionContext<RefT>>
        (_store, *_dict, _allocator, std::move(bufferIdsToCompact));
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
vespalib::MemoryUsage
UniqueStore<EntryT, RefT, Compare, Allocator>::getMemoryUsage() const
{
    vespalib::MemoryUsage usage = _store.getMemoryUsage();
    usage.merge(_dict->get_memory_usage());
    return usage;
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
const BufferState &
UniqueStore<EntryT, RefT, Compare, Allocator>::bufferState(EntryRef ref) const
{
    RefT internalRef(ref);
    return _store.getBufferState(internalRef.bufferId());
}


template <typename EntryT, typename RefT, typename Compare, typename Allocator>
void
UniqueStore<EntryT, RefT, Compare, Allocator>::transferHoldLists(generation_t generation)
{
    _dict->transfer_hold_lists(generation);
    _store.transferHoldLists(generation);
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
void
UniqueStore<EntryT, RefT, Compare, Allocator>::trimHoldLists(generation_t firstUsed)
{
    _dict->trim_hold_lists(firstUsed);
    _store.trimHoldLists(firstUsed);
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
void
UniqueStore<EntryT, RefT, Compare, Allocator>::freeze()
{
    _dict->freeze();
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
typename UniqueStore<EntryT, RefT, Compare, Allocator>::Builder
UniqueStore<EntryT, RefT, Compare, Allocator>::getBuilder(uint32_t uniqueValuesHint)
{
    return Builder(_allocator, *_dict, uniqueValuesHint);
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
typename UniqueStore<EntryT, RefT, Compare, Allocator>::Enumerator
UniqueStore<EntryT, RefT, Compare, Allocator>::getEnumerator() const
{
    return Enumerator(*_dict, _store);
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
uint32_t
UniqueStore<EntryT, RefT, Compare, Allocator>::getNumUniques() const
{
    return _dict->get_num_uniques();
}

}
