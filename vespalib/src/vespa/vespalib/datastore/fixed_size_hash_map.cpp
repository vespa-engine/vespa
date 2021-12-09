// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fixed_size_hash_map.h"
#include "entry_comparator.h"
#include "entry_ref_filter.h"
#include "i_compactable.h"
#include <vespa/vespalib/util/array.hpp>
#include <vespa/vespalib/util/memoryusage.h>
#include <cassert>
#include <stdexcept>

namespace vespalib::datastore {

FixedSizeHashMap::Node::Node(Node&&)
{
    throw std::runtime_error("vespalib::datastore::FixedSizeHashMap::Node move constructor should never be called");
}

void
FixedSizeHashMap::Node::on_free()
{
    _kv = std::make_pair(AtomicEntryRef(), AtomicEntryRef());
}

FixedSizeHashMap::FixedSizeHashMap(uint32_t modulo, uint32_t capacity, uint32_t num_shards)
    : _chain_heads(modulo),
      _nodes(),
      _modulo(modulo),
      _count(0u),
      _free_head(no_node_idx),
      _free_count(0u),
      _hold_count(0u),
      _hold_1_list(),
      _hold_2_list(),
      _num_shards(num_shards)
{
    _nodes.reserve(capacity);
}

FixedSizeHashMap::FixedSizeHashMap(uint32_t modulo, uint32_t capacity, uint32_t num_shards, const FixedSizeHashMap &orig, const EntryComparator& comp)
    : FixedSizeHashMap(modulo, capacity, num_shards)
{
    for (const auto &chain_head : orig._chain_heads) {
        for (uint32_t node_idx = chain_head.load_relaxed(); node_idx != no_node_idx;) {
            auto& node = orig._nodes[node_idx];
            force_add(comp, node.get_kv());
            node_idx = node.get_next_node_idx().load(std::memory_order_relaxed);
        }
    }
}

FixedSizeHashMap::~FixedSizeHashMap() = default;

void
FixedSizeHashMap::force_add(const EntryComparator& comp, const KvType& kv)
{
    ShardedHashComparator shardedComp(comp, kv.first.load_relaxed(), _num_shards);
    uint32_t hash_idx = shardedComp.hash_idx() % _modulo;
    auto& chain_head = _chain_heads[hash_idx];
    assert(_nodes.size() < _nodes.capacity());
    uint32_t node_idx = _nodes.size();
    new (_nodes.push_back_fast()) Node(kv, chain_head.load_relaxed());
    chain_head.set(node_idx);
    ++_count;
}

FixedSizeHashMap::KvType&
FixedSizeHashMap::add(const ShardedHashComparator & comp, std::function<EntryRef(void)>& insert_entry)
{
    uint32_t hash_idx = comp.hash_idx() % _modulo;
    auto& chain_head = _chain_heads[hash_idx];
    uint32_t node_idx = chain_head.load_relaxed();
    while (node_idx != no_node_idx) {
        auto& node = _nodes[node_idx];
        if (comp.equal(node.get_kv().first.load_relaxed())) {
            return node.get_kv();
        }
        node_idx = node.get_next_node_idx().load(std::memory_order_relaxed);
    }
    if (_free_head != no_node_idx) {
        node_idx = _free_head;
        auto& node = _nodes[node_idx];
        _free_head = node.get_next_node_idx().load(std::memory_order_relaxed);
        --_free_count;
        node.get_kv().first.store_release(insert_entry());
        node.get_next_node_idx().store(chain_head.load_relaxed());
        chain_head.set(node_idx);
        ++_count;
        return node.get_kv();
    }
    assert(_nodes.size() < _nodes.capacity());
    node_idx = _nodes.size();
    new (_nodes.push_back_fast()) Node(std::make_pair(AtomicEntryRef(insert_entry()), AtomicEntryRef()), chain_head.load_relaxed());
    chain_head.set(node_idx);
    ++_count;
    return _nodes[node_idx].get_kv();
}

void
FixedSizeHashMap::transfer_hold_lists_slow(generation_t generation)
{
    auto &hold_2_list = _hold_2_list;
    for (uint32_t node_idx : _hold_1_list) {
        hold_2_list.push_back(std::make_pair(generation, node_idx));
    }
    _hold_1_list.clear();

}


void
FixedSizeHashMap::trim_hold_lists_slow(generation_t first_used)
{
    while (!_hold_2_list.empty()) {
        auto& first = _hold_2_list.front();
        if (static_cast<sgeneration_t>(first.first - first_used) >= 0) {
            break;
        }
        uint32_t node_idx = first.second;
        auto& node = _nodes[node_idx];
        node.get_next_node_idx().store(_free_head, std::memory_order_relaxed);
        _free_head = node_idx;
        ++_free_count;
        --_hold_count;
        node.on_free();
        _hold_2_list.erase(_hold_2_list.begin());
    }
}

FixedSizeHashMap::KvType*
FixedSizeHashMap::remove(const ShardedHashComparator & comp)
{
    uint32_t hash_idx = comp.hash_idx() % _modulo;
    auto& chain_head = _chain_heads[hash_idx];
    uint32_t node_idx = chain_head.load_relaxed();
    uint32_t prev_node_idx = no_node_idx;
    while (node_idx != no_node_idx) {
        auto &node = _nodes[node_idx];
        uint32_t next_node_idx = node.get_next_node_idx().load(std::memory_order_relaxed);
        if (comp.equal(node.get_kv().first.load_relaxed())) {
            if (prev_node_idx != no_node_idx) {
                _nodes[prev_node_idx].get_next_node_idx().store(next_node_idx, std::memory_order_release);
            } else {
                chain_head.set(next_node_idx);
            }
            --_count;
            ++_hold_count;
            _hold_1_list.push_back(node_idx);
            return &_nodes[node_idx].get_kv();
        }
        prev_node_idx = node_idx;
        node_idx = next_node_idx;
    }
    return nullptr;
}

MemoryUsage
FixedSizeHashMap::get_memory_usage() const
{
    size_t fixed_size = sizeof(FixedSizeHashMap);
    size_t chain_heads_size = sizeof(ChainHead) * _chain_heads.size();
    size_t nodes_used_size = sizeof(Node) * _nodes.size();
    size_t nodes_alloc_size = sizeof(Node) * _nodes.capacity();
    size_t nodes_dead_size = sizeof(Node) * _free_count;
    size_t nodes_hold_size = sizeof(Node) * _hold_count;
    return MemoryUsage(fixed_size + chain_heads_size + nodes_alloc_size,
                       fixed_size + chain_heads_size + nodes_used_size,
                       nodes_dead_size,
                       nodes_hold_size);
}

void
FixedSizeHashMap::foreach_key(const std::function<void(EntryRef)>& callback) const
{
    for (auto& chain_head : _chain_heads) {
        uint32_t node_idx = chain_head.load_relaxed();
        while (node_idx != no_node_idx) {
            auto& node = _nodes[node_idx];
            callback(node.get_kv().first.load_relaxed());
            node_idx = node.get_next_node_idx().load(std::memory_order_relaxed);
        }
    }
}

void
FixedSizeHashMap::move_keys(ICompactable& compactable, const EntryRefFilter &compacting_buffers)
{
    for (auto& chain_head : _chain_heads) {
        uint32_t node_idx = chain_head.load_relaxed();
        while (node_idx != no_node_idx) {
            auto& node = _nodes[node_idx];
            EntryRef old_ref = node.get_kv().first.load_relaxed();
            assert(old_ref.valid());
            if (compacting_buffers.has(old_ref)) {
                EntryRef new_ref = compactable.move(old_ref);
                node.get_kv().first.store_release(new_ref);
            }
            node_idx = node.get_next_node_idx().load(std::memory_order_relaxed);
        }
    }
}

bool
FixedSizeHashMap::normalize_values(const std::function<EntryRef(EntryRef)>& normalize)
{
    bool changed = false;
    for (auto& chain_head : _chain_heads) {
        uint32_t node_idx = chain_head.load_relaxed();
        while (node_idx != no_node_idx) {
            auto& node = _nodes[node_idx];
            EntryRef old_ref = node.get_kv().second.load_relaxed();
            EntryRef new_ref = normalize(old_ref);
            if (new_ref != old_ref) {
                node.get_kv().second.store_release(new_ref);
                changed = true;
            }
            node_idx = node.get_next_node_idx().load(std::memory_order_relaxed);
        }
    }
    return changed;
}

namespace {

class ChangeWriter {
    std::vector<AtomicEntryRef*> _atomic_refs;
public:
    ChangeWriter(uint32_t capacity);
    ~ChangeWriter();
    bool write(const std::vector<EntryRef> &refs);
    void emplace_back(AtomicEntryRef &atomic_ref) { _atomic_refs.emplace_back(&atomic_ref); }
};

ChangeWriter::ChangeWriter(uint32_t capacity)
    : _atomic_refs()
{
    _atomic_refs.reserve(capacity);
}

ChangeWriter::~ChangeWriter() = default;

bool
ChangeWriter::write(const std::vector<EntryRef> &refs)
{
    bool changed = false;
    assert(refs.size() == _atomic_refs.size());
    auto atomic_ref = _atomic_refs.begin();
    for (auto ref : refs) {
        EntryRef old_ref = (*atomic_ref)->load_relaxed();
        if (ref != old_ref) {
            (*atomic_ref)->store_release(ref);
            changed = true;
        }
        ++atomic_ref;
    }
    assert(atomic_ref == _atomic_refs.end());
    _atomic_refs.clear();
    return changed;
}

}

bool
FixedSizeHashMap::normalize_values(const std::function<void(std::vector<EntryRef>&)>& normalize, const EntryRefFilter& filter)
{
    std::vector<EntryRef> refs;
    refs.reserve(1024);
    bool changed = false;
    ChangeWriter change_writer(refs.capacity());
    for (auto& chain_head : _chain_heads) {
        uint32_t node_idx = chain_head.load_relaxed();
        while (node_idx != no_node_idx) {
            auto& node = _nodes[node_idx];
            EntryRef ref = node.get_kv().second.load_relaxed();
            if (ref.valid()) {
                if (filter.has(ref)) {
                    refs.emplace_back(ref);
                    change_writer.emplace_back(node.get_kv().second);
                    if (refs.size() >= refs.capacity()) {
                        normalize(refs);
                        changed |= change_writer.write(refs);
                        refs.clear();
                    }
                }
            }
            node_idx = node.get_next_node_idx().load(std::memory_order_relaxed);
        }
    }
    if (!refs.empty()) {
        normalize(refs);
        changed |= change_writer.write(refs);
    }
    return changed;
}

void
FixedSizeHashMap::foreach_value(const std::function<void(const std::vector<EntryRef>&)>& callback, const EntryRefFilter& filter)
{
    std::vector<EntryRef> refs;
    refs.reserve(1024);
    for (auto& chain_head : _chain_heads) {
        uint32_t node_idx = chain_head.load_relaxed();
        while (node_idx != no_node_idx) {
            auto& node = _nodes[node_idx];
            EntryRef ref = node.get_kv().second.load_relaxed();
            if (ref.valid()) {
                if (filter.has(ref)) {
                    refs.emplace_back(ref);
                    if (refs.size() >= refs.capacity()) {
                        callback(refs);
                        refs.clear();
                    }
                }
            }
            node_idx = node.get_next_node_idx().load(std::memory_order_relaxed);
        }
    }
    if (!refs.empty()) {
        callback(refs);
    }
}

}
