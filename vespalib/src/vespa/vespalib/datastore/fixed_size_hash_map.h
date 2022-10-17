// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "atomic_entry_ref.h"
#include "entry_comparator.h"
#include <vespa/vespalib/util/array.h>
#include <vespa/vespalib/util/arrayref.h>
#include <vespa/vespalib/util/generation_hold_list.h>
#include <vespa/vespalib/util/generationhandler.h>
#include <atomic>
#include <deque>
#include <functional>
#include <limits>

namespace vespalib {
class GenerationHolder;
class MemoryUsage;
}
namespace vespalib::datastore {

class EntryRefFilter;
struct ICompactable;

class ShardedHashComparator {
public:
    ShardedHashComparator(const EntryComparator& comp, const EntryRef key_ref, uint32_t num_shards)
        : _comp(comp),
          _key_ref(key_ref)
    {
        size_t hash = comp.hash(key_ref);
        _shard_idx = hash % num_shards;
        _hash_idx = hash / num_shards;
    }
    uint32_t hash_idx() const { return _hash_idx; }
    uint32_t shard_idx() const { return _shard_idx; }
    bool equal(const EntryRef rhs) const {
        return _comp.equal(_key_ref, rhs);
    }
private:
    const EntryComparator& _comp;
    const EntryRef         _key_ref;
    uint32_t               _shard_idx;
    uint32_t               _hash_idx;
};

/*
 * Fixed sized hash map over keys in data store, meant to support a faster
 * dictionary for unique store with relation to lookups.
 *
 * Currently hardcoded key and data types, where key references an entry
 * in a UniqueStore and value references a posting list
 * (cf. search::attribute::PostingStore).
 *
 * This structure supports one writer and many readers.
 *
 * A reader must own an appropriate GenerationHandler::Guard to ensure
 * that memory is held while it can be accessed by reader.
 *
 * The writer must update generation and call assign_generation and
 * reclaim_memory as needed to free up memory no longer needed by any
 * readers.
 */
class FixedSizeHashMap {
public:
    static constexpr uint32_t no_node_idx = std::numeric_limits<uint32_t>::max();
    using KvType = std::pair<AtomicEntryRef, AtomicEntryRef>;
    using generation_t = GenerationHandler::generation_t;
private:
    class ChainHead {
        std::atomic<uint32_t> _node_idx;

    public:
        ChainHead()
            : _node_idx(no_node_idx)
        {
        }
        // Writer thread
        uint32_t load_relaxed() const noexcept { return _node_idx.load(std::memory_order_relaxed); }
        void set(uint32_t node_idx) { _node_idx.store(node_idx, std::memory_order_release); }

        // Reader thread
        uint32_t load_acquire() const noexcept { return _node_idx.load(std::memory_order_acquire); }
    };
    class Node {
        KvType _kv;
        std::atomic<uint32_t> _next_node_idx;
    public:
        Node()
            : Node(std::make_pair(AtomicEntryRef(), AtomicEntryRef()), no_node_idx)
        {
        }
        Node(KvType kv, uint32_t next_node_idx)
            : _kv(kv),
              _next_node_idx(next_node_idx)
        {
        }
        Node(Node &&rhs); // Must be defined, but must never be used.
        void on_free();
        std::atomic<uint32_t>& get_next_node_idx() noexcept { return _next_node_idx; }
        const std::atomic<uint32_t>& get_next_node_idx() const noexcept { return _next_node_idx; }
        KvType& get_kv() noexcept { return _kv; }
        const KvType& get_kv() const noexcept { return _kv; }
    };

    using NodeIdxHoldList = GenerationHoldList<uint32_t, false, true>;

    Array<ChainHead>  _chain_heads;
    Array<Node>       _nodes;
    uint32_t          _modulo;
    uint32_t          _count;
    uint32_t          _free_head;
    uint32_t          _free_count;
    uint32_t          _hold_count;
    NodeIdxHoldList   _hold_list;
    uint32_t          _num_shards;

    void force_add(const EntryComparator& comp, const KvType& kv);
public:
    FixedSizeHashMap(uint32_t module, uint32_t capacity, uint32_t num_shards);
    FixedSizeHashMap(uint32_t module, uint32_t capacity, uint32_t num_shards, const FixedSizeHashMap &orig, const EntryComparator& comp);
    ~FixedSizeHashMap();

    ShardedHashComparator get_comp(const EntryComparator& comp) {
        return ShardedHashComparator(comp, EntryRef(), _num_shards);
    }

    KvType& add(const ShardedHashComparator & comp, std::function<EntryRef(void)>& insert_entry);
    KvType* remove(const ShardedHashComparator & comp);
    KvType* find(const ShardedHashComparator & comp) {
        uint32_t hash_idx = comp.hash_idx() % _modulo;
        auto& chain_head = _chain_heads[hash_idx];
        uint32_t node_idx = chain_head.load_acquire();
        while (node_idx != no_node_idx) {
            auto &node = _nodes[node_idx];
            EntryRef node_key_ref = node.get_kv().first.load_acquire();
            if (node_key_ref.valid() && comp.equal(node_key_ref)) {
                return &_nodes[node_idx].get_kv();
            }
            node_idx = node.get_next_node_idx().load(std::memory_order_acquire);
        }
        return nullptr;
    }

    void assign_generation(generation_t current_gen) {
        _hold_list.assign_generation(current_gen);
    }

    void reclaim_memory(generation_t oldest_used_gen);

    bool full() const noexcept { return _nodes.size() == _nodes.capacity() && _free_count == 0u; }
    size_t size() const noexcept { return _count; }
    MemoryUsage get_memory_usage() const;
    void foreach_key(const std::function<void(EntryRef)>& callback) const;
    void move_keys_on_compact(ICompactable& compactable, const EntryRefFilter &compacting_buffers);
    /*
     * Scan dictionary and call normalize function for each value. If
     * returned value is different then write back the modified value to
     * the dictionary. Used when clearing all posting lists.
     */
    bool normalize_values(const std::function<EntryRef(EntryRef)>& normalize);
    /*
     * Scan dictionary and call normalize function for batches of values
     * that pass the filter. Write back modified values to the dictionary.
     * Used by compaction of posting lists when moving short arrays,
     * bitvectors or btree roots.
     */
    bool normalize_values(const std::function<void(std::vector<EntryRef>&)>& normalize, const EntryRefFilter& filter);
    /*
     * Scan dictionary and call callback function for batches of values
     * that pass the filter. Used by compaction of posting lists when
     * moving btree nodes.
     */
    void foreach_value(const std::function<void(const std::vector<EntryRef>&)>& callback, const EntryRefFilter& filter);
};

}

namespace vespalib {
extern template class GenerationHoldList<uint32_t, false, true>;
}
