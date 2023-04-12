// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "unique_store.h"
#include "unique_store_dictionary.h"
#include "unique_store_remapper.h"
#include "datastore.hpp"
#include "unique_store_allocator.hpp"
#include "unique_store_builder.hpp"
#include "unique_store_dictionary.hpp"
#include "unique_store_enumerator.hpp"
#include "unique_store_remapper.hpp"
#include <atomic>
#include <algorithm>

namespace vespalib::datastore {

namespace uniquestore {

using DefaultDictionaryTraits = btree::BTreeTraits<32, 32, 7, true>;
using DefaultDictionary = btree::BTree<AtomicEntryRef, btree::BTreeNoLeafData,
                                       btree::NoAggregated,
                                       EntryComparatorWrapper,
                                       DefaultDictionaryTraits>;
using DefaultUniqueStoreDictionary = UniqueStoreDictionary<DefaultDictionary>;

}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
UniqueStore<EntryT, RefT, Compare, Allocator>::UniqueStore(std::shared_ptr<alloc::MemoryAllocator> memory_allocator)
    : UniqueStore<EntryT, RefT, Compare, Allocator>(std::make_unique<uniquestore::DefaultUniqueStoreDictionary>(std::unique_ptr<EntryComparator>()), std::move(memory_allocator))
{
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
UniqueStore<EntryT, RefT, Compare, Allocator>::UniqueStore(std::unique_ptr<IUniqueStoreDictionary> dict, std::shared_ptr<alloc::MemoryAllocator> memory_allocator)
    : _allocator(std::move(memory_allocator)),
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
    using UniqueStoreRemapper<RefT>::_filter;
    using UniqueStoreRemapper<RefT>::_mapping;
    IUniqueStoreDictionary &_dict;
    ICompactable &_store;
    std::unique_ptr<CompactingBuffers> _compacting_buffers;

    void allocMapping() {
        auto& data_store = _compacting_buffers->get_store();
        _mapping.resize(data_store.get_bufferid_limit_relaxed());
        for (const auto bufferId : _compacting_buffers->get_buffer_ids()) {
            BufferState &state = data_store.getBufferState(bufferId);
            _mapping[bufferId].resize(state.size());
        }
    }

    EntryRef move_on_compact(EntryRef oldRef) override {
        RefT iRef(oldRef);
        uint32_t buffer_id = iRef.bufferId();
        auto &inner_mapping = _mapping[buffer_id];
        assert(iRef.offset() < inner_mapping.size());
        EntryRef &mappedRef = inner_mapping[iRef.offset()];
        assert(!mappedRef.valid());
        EntryRef newRef = _store.move_on_compact(oldRef);
        mappedRef = newRef;
        return newRef;
    }
    
    void fillMapping() {
        _dict.move_keys_on_compact(*this, _filter);
    }

public:
    CompactionContext(IUniqueStoreDictionary &dict,
                      ICompactable &store,
                      std::unique_ptr<CompactingBuffers> compacting_buffers)
        : UniqueStoreRemapper<RefT>(compacting_buffers->make_entry_ref_filter()),
          ICompactable(),
          _dict(dict),
          _store(store),
          _compacting_buffers(std::move(compacting_buffers))
    {
        if (!_compacting_buffers->empty()) {
            allocMapping();
            fillMapping();
        }
    }

    void done() override {
        _compacting_buffers->finish();
    }
    ~CompactionContext() override {
        assert(_compacting_buffers->empty());
    }
};

}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
std::unique_ptr<typename UniqueStore<EntryT, RefT, Compare, Allocator>::Remapper>
UniqueStore<EntryT, RefT, Compare, Allocator>::compact_worst(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy)
{
    auto compacting_buffers = _store.start_compact_worst_buffers(compaction_spec, compaction_strategy);
    if (compacting_buffers->empty()) {
        return std::unique_ptr<Remapper>();
    } else {
        return std::make_unique<uniquestore::CompactionContext<RefT>>(*_dict, _allocator, std::move(compacting_buffers));
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
UniqueStore<EntryT, RefT, Compare, Allocator>::get_values_address_space_usage() const
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
UniqueStore<EntryT, RefT, Compare, Allocator>::assign_generation(generation_t current_gen)
{
    _dict->assign_generation(current_gen);
    _store.assign_generation(current_gen);
}

template <typename EntryT, typename RefT, typename Compare, typename Allocator>
void
UniqueStore<EntryT, RefT, Compare, Allocator>::reclaim_memory(generation_t oldest_used_gen)
{
    _dict->reclaim_memory(oldest_used_gen);
    _store.reclaim_memory(oldest_used_gen);
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
UniqueStore<EntryT, RefT, Compare, Allocator>::getEnumerator(bool sort_unique_values)
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
