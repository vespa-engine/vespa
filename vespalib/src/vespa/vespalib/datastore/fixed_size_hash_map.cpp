// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "fixed_size_hash_map.h"
#include "entry_comparator.h"
#include <vespa/vespalib/util/array.hpp>
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

FixedSizeHashMap::FixedSizeHashMap(uint32_t modulo, uint32_t capacity, uint32_t num_stripes)
    : _chain_heads(modulo),
      _nodes(),
      _modulo(modulo),
      _count(0u),
      _free_head(no_node_idx),
      _free_count(0u),
      _hold_count(0u),
      _hold_1_list(),
      _hold_2_list(),
      _num_stripes(num_stripes)
{
    _nodes.reserve(capacity);
}

FixedSizeHashMap::FixedSizeHashMap(uint32_t modulo, uint32_t capacity, uint32_t num_stripes, const FixedSizeHashMap &orig, const EntryComparator& comp)
    : FixedSizeHashMap(modulo, capacity, num_stripes)
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
    size_t hash_idx = comp.hash(kv.first.load_relaxed()) / _num_stripes;
    hash_idx %= _modulo;
    auto& chain_head = _chain_heads[hash_idx];
    assert(_nodes.size() < _nodes.capacity());
    uint32_t node_idx = _nodes.size();
    new (_nodes.push_back_fast()) Node(kv, chain_head.load_relaxed());
    chain_head.set(node_idx);
    ++_count;
}

FixedSizeHashMap::KvType&
FixedSizeHashMap::add(const EntryComparator& comp, std::function<EntryRef(void)>& insert_entry)
{
    size_t hash_idx = comp.hash(EntryRef()) / _num_stripes;
    hash_idx %= _modulo;
    auto& chain_head = _chain_heads[hash_idx];
    uint32_t node_idx = chain_head.load_relaxed();
    while (node_idx != no_node_idx) {
        auto& node = _nodes[node_idx];
        if (comp.equal(EntryRef(), node.get_kv().first.load_relaxed())) {
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
FixedSizeHashMap::remove(const EntryComparator& comp, EntryRef key_ref)
{
    size_t hash_idx = comp.hash(key_ref) / _num_stripes;
    hash_idx %= _modulo;
    auto& chain_head = _chain_heads[hash_idx];
    uint32_t node_idx = chain_head.load_relaxed();
    uint32_t prev_node_idx = no_node_idx;
    while (node_idx != no_node_idx) {
        auto &node = _nodes[node_idx];
        uint32_t next_node_idx = node.get_next_node_idx().load(std::memory_order_relaxed);
        if (comp.equal(key_ref, node.get_kv().first.load_relaxed())) {
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

const FixedSizeHashMap::KvType*
FixedSizeHashMap::find(const EntryComparator& comp, EntryRef key_ref) const
{
    size_t hash_idx = comp.hash(key_ref) / _num_stripes;
    hash_idx %= _modulo;
    auto& chain_head = _chain_heads[hash_idx];
    uint32_t node_idx = chain_head.load_acquire();
    while (node_idx != no_node_idx) {
        auto &node = _nodes[node_idx];
        EntryRef node_key_ref = node.get_kv().first.load_acquire();
        if (node_key_ref.valid() && comp.equal(key_ref, node_key_ref)) {
            return &_nodes[node_idx].get_kv();
        }
        node_idx = node.get_next_node_idx().load(std::memory_order_acquire);
    }
    return nullptr;
}

}
