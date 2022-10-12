// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "buffer_type.h"
#include "bufferstate.h"
#include "datastore.h"
#include "entry_comparator_wrapper.h"
#include "entryref.h"
#include "i_unique_store_dictionary.h"
#include "unique_store_add_result.h"
#include "unique_store_allocator.h"
#include "unique_store_comparator.h"
#include "unique_store_entry.h"

namespace vespalib::alloc { class MemoryAllocator; }

namespace vespalib::datastore {

template <typename Allocator>
class UniqueStoreBuilder;

template <typename RefT>
class UniqueStoreEnumerator;

template <typename RefT>
class UniqueStoreRemapper;

/**
 * Datastore for unique values of type EntryT that is accessed via a
 * 32-bit EntryRef.
 */
template <typename EntryT, typename RefT = EntryRefT<22>, typename Compare = UniqueStoreComparator<EntryT, RefT>, typename Allocator = UniqueStoreAllocator<EntryT, RefT> >
class UniqueStore
{
public:
    using DataStoreType = DataStoreT<RefT>;
    using EntryType = EntryT;
    using RefType = RefT;
    using CompareType = Compare;
    using Enumerator = UniqueStoreEnumerator<RefT>;
    using Builder = UniqueStoreBuilder<Allocator>;
    using Remapper = UniqueStoreRemapper<RefT>;
    using EntryConstRefType = typename Allocator::EntryConstRefType;
private:
    Allocator _allocator;
    DataStoreType &_store;
    std::unique_ptr<IUniqueStoreDictionary> _dict;
    using generation_t = vespalib::GenerationHandler::generation_t;

public:
    UniqueStore(std::shared_ptr<alloc::MemoryAllocator> memory_allocator);
    UniqueStore(std::unique_ptr<IUniqueStoreDictionary> dict, std::shared_ptr<alloc::MemoryAllocator> memory_allocator);
    ~UniqueStore();
    void set_dictionary(std::unique_ptr<IUniqueStoreDictionary> dict);
    UniqueStoreAddResult add(EntryConstRefType value);
    EntryRef find(EntryConstRefType value);
    EntryConstRefType get(EntryRef ref) const { return _allocator.get(ref); }
    void remove(EntryRef ref);
    std::unique_ptr<Remapper> compact_worst(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy);
    vespalib::MemoryUsage getMemoryUsage() const;
    vespalib::MemoryUsage get_values_memory_usage() const { return _store.getMemoryUsage(); }
    vespalib::MemoryUsage get_dictionary_memory_usage() const { return _dict->get_memory_usage(); }
    vespalib::AddressSpace get_values_address_space_usage() const;

    // TODO: Consider exposing only the needed functions from allocator
    Allocator& get_allocator() { return _allocator; }
    const Allocator& get_allocator() const { return _allocator; }
    IUniqueStoreDictionary& get_dictionary() { return *_dict; }
    inline const DataStoreType& get_data_store() const noexcept { return _allocator.get_data_store(); }

    // Pass on hold list management to underlying store
    void assign_generation(generation_t current_gen);
    void reclaim_memory(generation_t oldest_used_gen);
    vespalib::GenerationHolder &getGenerationHolder() { return _store.getGenerationHolder(); }
    void setInitializing(bool initializing) { _store.setInitializing(initializing); }
    void freeze();
    uint32_t getNumUniques() const;

    Builder getBuilder(uint32_t uniqueValuesHint);
    Enumerator getEnumerator(bool sort_unique_values) const;

    // Should only be used for unit testing
    const BufferState &bufferState(EntryRef ref) const;
};

}
