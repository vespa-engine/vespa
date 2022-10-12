// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "entryref.h"
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <vespa/vespalib/util/memoryusage.h>
#include <functional>

namespace vespalib::datastore {

class CompactionStrategy;
class EntryComparator;
class EntryRefFilter;
struct ICompactable;
class IUniqueStoreDictionaryReadSnapshot;
class UniqueStoreAddResult;

/**
 * Interface class for unique store dictionary.
 */
class IUniqueStoreDictionary {
public:
    using generation_t = vespalib::GenerationHandler::generation_t;
    virtual ~IUniqueStoreDictionary() = default;
    virtual void freeze() = 0;
    virtual void assign_generation(generation_t current_gen) = 0;
    virtual void reclaim_memory(generation_t oldest_used_gen) = 0;
    virtual UniqueStoreAddResult add(const EntryComparator& comp, std::function<EntryRef(void)> insertEntry) = 0;
    virtual EntryRef find(const EntryComparator& comp) = 0;
    virtual void remove(const EntryComparator& comp, EntryRef ref) = 0;
    virtual void move_keys_on_compact(ICompactable& compactable, const EntryRefFilter& compacting_buffers) = 0;
    virtual uint32_t get_num_uniques() const = 0;
    virtual vespalib::MemoryUsage get_memory_usage() const = 0;
    virtual void build(vespalib::ConstArrayRef<EntryRef>, vespalib::ConstArrayRef<uint32_t> ref_counts, std::function<void(EntryRef)> hold) = 0;
    virtual void build(vespalib::ConstArrayRef<EntryRef> refs) = 0;
    virtual void build_with_payload(vespalib::ConstArrayRef<EntryRef> refs, vespalib::ConstArrayRef<EntryRef> payloads) = 0;
    virtual std::unique_ptr<IUniqueStoreDictionaryReadSnapshot> get_read_snapshot() const = 0;
    virtual bool get_has_btree_dictionary() const = 0;
    virtual bool get_has_hash_dictionary() const = 0;
    virtual vespalib::MemoryUsage get_btree_memory_usage() const = 0;
    virtual vespalib::MemoryUsage get_hash_memory_usage() const = 0;
    virtual bool has_held_buffers() const = 0;
    virtual void compact_worst(bool compact_btree_dictionary, bool compact_hash_dictionary, const CompactionStrategy& compaction_strategy) = 0;
};

}
