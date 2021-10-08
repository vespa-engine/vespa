// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "sharded_hash_map.h"
#include "fixed_size_hash_map.h"
#include "entry_comparator.h"
#include <vespa/vespalib/util/memoryusage.h>

namespace vespalib::datastore {

class ShardedHashMapShardHeld : public GenerationHeldBase
{
    std::unique_ptr<const FixedSizeHashMap> _data;
public:
    ShardedHashMapShardHeld(size_t size, std::unique_ptr<const FixedSizeHashMap> data);
    ~ShardedHashMapShardHeld() override;
};

ShardedHashMapShardHeld::ShardedHashMapShardHeld(size_t size, std::unique_ptr<const FixedSizeHashMap> data)
    : GenerationHeldBase(size),
      _data(std::move(data))
{
}

ShardedHashMapShardHeld::~ShardedHashMapShardHeld() = default;

ShardedHashMap::ShardedHashMap(std::unique_ptr<const EntryComparator> comp)
    : _gen_holder(),
      _maps(),
      _comp(std::move(comp))
{
}

ShardedHashMap::~ShardedHashMap()
{
    _gen_holder.clearHoldLists();
    for (size_t i = 0; i < num_shards; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        delete map;
    }
}

void
ShardedHashMap::alloc_shard(size_t shard_idx)
{
    auto map = _maps[shard_idx].load(std::memory_order_relaxed);
    if (map == nullptr) {
        auto umap = std::make_unique<FixedSizeHashMap>(2u, 3u, num_shards);
        _maps[shard_idx].store(umap.release(), std::memory_order_release);
    } else {
        auto umap = std::make_unique<FixedSizeHashMap>(map->size() * 2 + 2, map->size() * 3 + 3, num_shards, *map, *_comp);
        _maps[shard_idx].store(umap.release(), std::memory_order_release);
        hold_shard(std::unique_ptr<const FixedSizeHashMap>(map));
    }
}

void
ShardedHashMap::hold_shard(std::unique_ptr<const FixedSizeHashMap> map)
{
    auto usage = map->get_memory_usage();
    auto hold = std::make_unique<ShardedHashMapShardHeld>(usage.allocatedBytes(), std::move(map));
    _gen_holder.hold(std::move(hold));
}

ShardedHashMap::KvType&
ShardedHashMap::add(const EntryComparator& comp, EntryRef key_ref, std::function<EntryRef(void)>& insert_entry)
{
    ShardedHashComparator shardedComp(comp, key_ref, num_shards);
    auto map = _maps[shardedComp.shard_idx()].load(std::memory_order_relaxed);
    if (map == nullptr || map->full()) {
        alloc_shard(shardedComp.shard_idx());
        map = _maps[shardedComp.shard_idx()].load(std::memory_order_relaxed);
    }
    return map->add(shardedComp, insert_entry);
}

ShardedHashMap::KvType*
ShardedHashMap::remove(const EntryComparator& comp, EntryRef key_ref)
{
    ShardedHashComparator shardedComp(comp, key_ref, num_shards);
    auto map = _maps[shardedComp.shard_idx()].load(std::memory_order_relaxed);
    if (map == nullptr) {
        return nullptr;
    }
    return map->remove(shardedComp);
}

ShardedHashMap::KvType*
ShardedHashMap::find(const EntryComparator& comp, EntryRef key_ref)
{
    ShardedHashComparator shardedComp(comp, key_ref, num_shards);
    auto map = _maps[shardedComp.shard_idx()].load(std::memory_order_relaxed);
    if (map == nullptr) {
        return nullptr;
    }
    return map->find(shardedComp);
}

const ShardedHashMap::KvType*
ShardedHashMap::find(const EntryComparator& comp, EntryRef key_ref) const
{
    ShardedHashComparator shardedComp(comp, key_ref, num_shards);
    auto map = _maps[shardedComp.shard_idx()].load(std::memory_order_relaxed);
    if (map == nullptr) {
        return nullptr;
    }
    return map->find(shardedComp);
}

void
ShardedHashMap::transfer_hold_lists(generation_t generation)
{
    for (size_t i = 0; i < num_shards; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            map->transfer_hold_lists(generation);
        }
    }
    _gen_holder.transferHoldLists(generation);
}

void
ShardedHashMap::trim_hold_lists(generation_t first_used)
{
    for (size_t i = 0; i < num_shards; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            map->trim_hold_lists(first_used);
        }
    }
    _gen_holder.trimHoldLists(first_used);
}

size_t
ShardedHashMap::size() const noexcept
{
    size_t result = 0;
    for (size_t i = 0; i < num_shards; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            result += map->size();
        }
    }
    return result;
}

MemoryUsage
ShardedHashMap::get_memory_usage() const
{
    MemoryUsage memory_usage(sizeof(ShardedHashMap), sizeof(ShardedHashMap), 0, 0);
    for (size_t i = 0; i < num_shards; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            memory_usage.merge(map->get_memory_usage());
        }
    }
    size_t gen_holder_held_bytes = _gen_holder.getHeldBytes();
    memory_usage.incAllocatedBytes(gen_holder_held_bytes);
    memory_usage.incAllocatedBytesOnHold(gen_holder_held_bytes);
    return memory_usage;
}

void
ShardedHashMap::foreach_key(std::function<void(EntryRef)> callback) const
{
    for (size_t i = 0; i < num_shards; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            map->foreach_key(callback);
        }
    }
}

void
ShardedHashMap::move_keys(std::function<EntryRef(EntryRef)> callback)
{
    for (size_t i = 0; i < num_shards; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            map->move_keys(callback);
        }
    }
}

bool
ShardedHashMap::normalize_values(std::function<EntryRef(EntryRef)> normalize)
{
    bool changed = false;
    for (size_t i = 0; i < num_shards; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            changed |= map->normalize_values(normalize);
        }
    }
    return changed;
}

bool
ShardedHashMap::has_held_buffers() const
{
    return _gen_holder.getHeldBytes() != 0;
}

void
ShardedHashMap::compact_worst_shard()
{
    size_t worst_index = 0u;
    size_t worst_dead_bytes = 0u;
    for (size_t i = 0; i < num_shards; ++i) {
        auto map = _maps[i].load(std::memory_order_relaxed);
        if (map != nullptr) {
            auto memory_usage = map->get_memory_usage();
            if (memory_usage.deadBytes() > worst_dead_bytes) {
                worst_index = i;
                worst_dead_bytes = memory_usage.deadBytes();
            }
        }
    }
    if (worst_dead_bytes > 0u) {
        alloc_shard(worst_index);
    }
}

}
