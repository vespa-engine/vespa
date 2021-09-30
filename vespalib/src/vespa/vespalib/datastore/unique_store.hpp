// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store.h"
#include "unique_store_dictionary.h"
#include "unique_store_remapper.h"
#include "datastore.hpp"
#include "unique_store_allocator.hpp"
#include "unique_store_builder.hpp"
#include "unique_store_dictionary.hpp"
#include "unique_store_enumerator.hpp"
#include <atomic>
#include <algorithm>

namespace vespalib::datastore {

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
    : UniqueStore<EntryT, RefT, Compare, Allocator>(std::make_unique<uniquestore::DefaultUniqueStoreDictionary>(std::unique_ptr<EntryComparator>()))
{
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
UniqueStore<EntryT, RefT, Compare, Allocator>::UniqueStore(std::unique_ptr<IUniqueStoreDictionary> dict)
    : _allocator(),
      _store(_allocator.get_data_store()),
      _dict(std::move(dict))
{
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
UniqueStore<EntryT, RefT, Compare, Allocator>::~UniqueStore() = default;

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
void
UniqueStore<EntryT, RefT, Compare, Allocator>::set_dictionary(std::unique_ptr<IUniqueStoreDictionary> dict)
{
    _dict = std::move(dict);
}

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
class CompactionContext : public UniqueStoreRemapper<RefT>, public ICompactable {
private:
    using DictionaryTraits = btree::BTreeTraits<32, 32, 7, true>;
    using Dictionary = btree::BTree<EntryRef, uint32_t,
                                    btree::NoAggregated,
                                    EntryComparatorWrapper,
                                    DictionaryTraits>;
    using UniqueStoreRemapper<RefT>::_compacting_buffer;
    using UniqueStoreRemapper<RefT>::_mapping;
    DataStoreBase &_dataStore;
    IUniqueStoreDictionary &_dict;
    ICompactable &_store;
    std::vector<uint32_t> _bufferIdsToCompact;

    void allocMapping() {
        _compacting_buffer.resize(RefT::numBuffers());
        _mapping.resize(RefT::numBuffers());
        for (const auto bufferId : _bufferIdsToCompact) {
            BufferState &state = _dataStore.getBufferState(bufferId);
            _compacting_buffer[bufferId] = true;
            _mapping[bufferId].resize(state.get_used_arrays());
        }
    }

    EntryRef move(EntryRef oldRef) override {
        RefT iRef(oldRef);
        assert(iRef.valid());
        uint32_t buffer_id = iRef.bufferId();
        if (_compacting_buffer[buffer_id]) {
            auto &inner_mapping = _mapping[buffer_id];
            assert(iRef.unscaled_offset() < inner_mapping.size());
            EntryRef &mappedRef = inner_mapping[iRef.unscaled_offset()];
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
        : UniqueStoreRemapper<RefT>(),
          ICompactable(),
          _dataStore(dataStore),
          _dict(dict),
          _store(store),
          _bufferIdsToCompact(std::move(bufferIdsToCompact))
    {
        if (!_bufferIdsToCompact.empty()) {
            allocMapping();
            fillMapping();
        }
    }

    void done() override {
        _dataStore.finishCompact(_bufferIdsToCompact);
        _bufferIdsToCompact.clear();
    }
    ~CompactionContext() override {
        assert(_bufferIdsToCompact.empty());
    }
};

}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
std::unique_ptr<typename UniqueStore<EntryT, RefT, Compare, Allocator>::Remapper>
UniqueStore<EntryT, RefT, Compare, Allocator>::compact_worst(bool compact_memory, bool compact_address_space)
{
    std::vector<uint32_t> bufferIdsToCompact = _store.startCompactWorstBuffers(compact_memory, compact_address_space);
    if (bufferIdsToCompact.empty()) {
        return std::unique_ptr<Remapper>();
    } else {
        return std::make_unique<uniquestore::CompactionContext<RefT>>(_store, *_dict, _allocator, std::move(bufferIdsToCompact));
    }
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
vespalib::MemoryUsage
UniqueStore<EntryT, RefT, Compare, Allocator>::getMemoryUsage() const
{
    vespalib::MemoryUsage usage = get_values_memory_usage();
    usage.merge(get_dictionary_memory_usage());
    return usage;
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
vespalib::AddressSpace
UniqueStore<EntryT, RefT, Compare, Allocator>::get_address_space_usage() const
{
    return _allocator.get_data_store().getAddressSpaceUsage();
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
UniqueStore<EntryT, RefT, Compare, Allocator>::getEnumerator(bool sort_unique_values) const
{
    return Enumerator(*_dict, _store, sort_unique_values);
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
uint32_t
UniqueStore<EntryT, RefT, Compare, Allocator>::getNumUniques() const
{
    return _dict->get_num_uniques();
}

}
