// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/array_store_dynamic_type_mapper.h>
#include <vespa/vespalib/datastore/dynamic_array_buffer_type.h>

namespace search::attribute {

/**
 * Class handling storage of raw values in an array store. A stored entry
 * starts with 4 bytes that contains the size of the raw value.
 */
class RawBufferStore
{
    using EntryRef = vespalib::datastore::EntryRef;
    using RefType = vespalib::datastore::EntryRefT<19>;
    using TypeMapper = vespalib::datastore::ArrayStoreDynamicTypeMapper<char>;
    using ArrayStoreType = vespalib::datastore::ArrayStore<char, RefType, TypeMapper>;
    using generation_t = vespalib::GenerationHandler::generation_t;

    ArrayStoreType                      _array_store;
public:
    RawBufferStore(std::shared_ptr<vespalib::alloc::MemoryAllocator> allocator, uint32_t max_small_buffer_type_id, double grow_factor);
    ~RawBufferStore();
    EntryRef set(vespalib::ConstArrayRef<char> raw) { return _array_store.add(raw); };
    vespalib::ConstArrayRef<char> get(EntryRef ref) const { return _array_store.get(ref); }
    void remove(EntryRef ref) { _array_store.remove(ref); }
    vespalib::MemoryUsage update_stat(const vespalib::datastore::CompactionStrategy& compaction_strategy) { return _array_store.update_stat(compaction_strategy); }
    vespalib::AddressSpace get_address_space_usage() const { return _array_store.addressSpaceUsage(); }
    bool consider_compact() const noexcept { return _array_store.consider_compact(); }
    std::unique_ptr<vespalib::datastore::ICompactionContext> start_compact(const vespalib::datastore::CompactionStrategy& compaction_strategy) { return _array_store.compact_worst(compaction_strategy); }
    void reclaim_memory(generation_t oldest_used_gen) { _array_store.reclaim_memory(oldest_used_gen); }
    void assign_generation(generation_t current_gen) { _array_store.assign_generation(current_gen); }
    void set_initializing(bool initializing) { _array_store.setInitializing(initializing); }
};

}
