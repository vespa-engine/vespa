// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_nodeid_mapping.h"
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/util/generation_hold_list.hpp>
#include <vespa/vespalib/util/size_literals.h>
#include <cassert>

using vespalib::datastore::EntryRef;

namespace {

constexpr uint32_t max_small_array_type_id = 64;
constexpr size_t small_page_size = 4_Ki;
constexpr size_t min_num_arrays_for_new_buffer = 512_Ki;
constexpr float alloc_grow_factor = 0.3;

}

namespace search::tensor {

void
HnswNodeidMapping::ensure_refs_size(uint32_t docid)
{
    if (docid >= _refs.size()) {
        if (docid >= _refs.capacity()) {
            _refs.reserve(_grow_strategy.calc_new_size(docid + 1));
        }
        _refs.resize(docid + 1);
    }
}

uint32_t
HnswNodeidMapping::allocate_id()
{
    if (_free_list.empty()) {
        return _nodeid_limit++;
    }
    uint32_t id = _free_list.back();
    _free_list.pop_back();
    return id;
}

HnswNodeidMapping::HnswNodeidMapping()
    : _refs(),
      _grow_strategy(16, 1.0, 0, 0), // These are the same parameters as the default in rcuvector.h
      _nodeid_limit(1), // Starting with nodeid=1 matches that we also start with docid=1.
      _nodeids(NodeidStore::optimizedConfigForHugePage(max_small_array_type_id,
                                                       vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                       small_page_size,
                                                       min_num_arrays_for_new_buffer,
                                                       alloc_grow_factor).enable_free_lists(true), {}),
      _hold_list(),
      _free_list()
{
    _refs.reserve(_grow_strategy.getInitialCapacity());
}

HnswNodeidMapping::~HnswNodeidMapping()
{
    _hold_list.reclaim_all();
}

vespalib::ConstArrayRef<uint32_t>
HnswNodeidMapping::allocate_ids(uint32_t docid, uint32_t subspaces)
{
    ensure_refs_size(docid);
    assert(!_refs[docid].valid());
    if (subspaces == 0) {
        return {};
    }
    EntryRef ref = _nodeids.allocate(subspaces);
    auto nodeids = _nodeids.get_writable(ref);
    for (auto& nodeid : nodeids) {
        nodeid = allocate_id();
    }
    _refs[docid] = ref;
    return nodeids;
}

vespalib::ConstArrayRef<uint32_t>
HnswNodeidMapping::get_ids(uint32_t docid) const
{
    assert(docid < _refs.size());
    return _nodeids.get(_refs[docid]);
}

void
HnswNodeidMapping::free_ids(uint32_t docid)
{
    assert(docid < _refs.size());
    EntryRef ref = _refs[docid];
    assert(ref.valid());
    auto nodeids = _nodeids.get(ref);
    for (auto nodeid : nodeids) {
        _hold_list.insert(nodeid);
    }
    _nodeids.remove(ref);
    _refs[docid] = EntryRef();
}

void
HnswNodeidMapping::assign_generation(generation_t current_gen)
{
    _nodeids.assign_generation(current_gen);
    _hold_list.assign_generation(current_gen);
}

void
HnswNodeidMapping::reclaim_memory(generation_t oldest_used_gen)
{
    _nodeids.reclaim_memory(oldest_used_gen);
    _hold_list.reclaim(oldest_used_gen, [this](uint32_t nodeid) {
        _free_list.push_back(nodeid);
    });
}

namespace {

vespalib::MemoryUsage
get_refs_usage(const std::vector<EntryRef>& refs)
{
    vespalib::MemoryUsage result;
    result.incAllocatedBytes(sizeof(EntryRef) * refs.capacity());
    result.incUsedBytes(sizeof(EntryRef) * refs.size());
    return result;
}

}

vespalib::MemoryUsage
HnswNodeidMapping::memory_usage() const
{
    vespalib::MemoryUsage result;
    result.merge(get_refs_usage(_refs));
    result.merge(_nodeids.getMemoryUsage());
    // Note that the memory usage of the hold list and free list is not explicitly tracked
    // as their content are covered by the memory usage reported from the NodeidStore (array store).
    return result;
}

}
