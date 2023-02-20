// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_nodeid_mapping.h"
#include "hnsw_node.h"
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/util/generation_hold_list.hpp>
#include <vespa/vespalib/util/size_literals.h>
#include <cassert>

using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;

namespace {

constexpr uint32_t max_small_array_type_id = 64;
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
    : _refs(1),
      _grow_strategy(16, 1.0, 0, 0), // These are the same parameters as the default in rcuvector.h
      _nodeid_limit(1), // Starting with nodeid=1 matches that we also start with docid=1.
      _nodeids(NodeidStore::optimizedConfigForHugePage(max_small_array_type_id,
                                                       vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                       vespalib::alloc::MemoryAllocator::PAGE_SIZE,
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

uint32_t
get_docid_limit(vespalib::ConstArrayRef<HnswNode> nodes)
{
    uint32_t max_docid = 0;
    for (auto& node : nodes) {
        if (node.levels_ref().load_relaxed().valid()) {
            max_docid = std::max(node.acquire_docid(), max_docid);
        }
    }
    return max_docid + 1;
}

std::vector<uint32_t>
make_subspaces_histogram(vespalib::ConstArrayRef<HnswNode> nodes, uint32_t docid_limit)
{
    // Make histogram
    std::vector<uint32_t> histogram(docid_limit);
    for (auto& node : nodes) {
        if (node.levels_ref().load_relaxed().valid()) {
            auto docid = node.acquire_docid();
            auto subspace = node.acquire_subspace();
            auto &num_subspaces = histogram[docid];
            num_subspaces = std::max(num_subspaces, subspace + 1);
        }
    }
    assert(histogram[0] == 0);
    return histogram;
}

}

void
HnswNodeidMapping::allocate_docid_to_nodeids_mapping(std::vector<uint32_t> histogram)
{
    ensure_refs_size(histogram.size() - 1);
    uint32_t docid = 0;
    for (auto subspaces : histogram) {
        if (subspaces > 0) {
            auto ref = _nodeids.allocate(subspaces);
            _refs[docid] = ref;
            auto nodeids = _nodeids.get_writable(ref);
            for (auto& nodeid : nodeids) {
                nodeid = 0;
            }
        }
        ++docid;
    }
}

void
HnswNodeidMapping::populate_docid_to_nodeids_mapping_and_free_list(vespalib::ConstArrayRef<HnswNode> nodes)
{
    uint32_t nodeid = 0;
    for (auto& node : nodes) {
        if (node.levels_ref().load_relaxed().valid()) {
            auto docid = node.acquire_docid();
            auto subspace = node.acquire_subspace();
            auto nodeids = _nodeids.get_writable(_refs[docid]);
            assert(subspace < nodeids.size());
            assert(nodeids[subspace] == 0);
            nodeids[subspace] = nodeid;
        } else if (nodeid > 0) {
            _free_list.push_back(nodeid);
        }
        ++nodeid;
    }
    std::reverse(_free_list.begin(), _free_list.end());
    _nodeid_limit = nodes.size();
}

void
HnswNodeidMapping::assert_all_subspaces_have_valid_nodeid(uint32_t docid_limit)
{
    for (uint32_t docid = 0; docid < docid_limit; ++docid) {
        auto ref = _refs[docid];
        if (ref.valid()) {
            auto nodeids = _nodeids.get_writable(ref);
            for (auto nodeid : nodeids) {
                assert(nodeid != 0);
            }
        }
    }
}

void
HnswNodeidMapping::on_load(vespalib::ConstArrayRef<HnswNode> nodes)
{
    if (nodes.empty()) {
        return;
    }
    // Check that reserved nodeid is not used
    assert(!nodes[0].levels_ref().load_relaxed().valid());
    auto docid_limit = get_docid_limit(nodes);
    auto histogram = make_subspaces_histogram(nodes, docid_limit);    // Allocate mapping from docid to nodeids
    allocate_docid_to_nodeids_mapping(std::move(histogram));
    populate_docid_to_nodeids_mapping_and_free_list(nodes);
    assert_all_subspaces_have_valid_nodeid(docid_limit);
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

vespalib::MemoryUsage
HnswNodeidMapping::update_stat(const CompactionStrategy& compaction_strategy)
{
    vespalib::MemoryUsage result;
    result.merge(get_refs_usage(_refs));
    result.merge(_nodeids.update_stat(compaction_strategy));
    // Note that the memory usage of the hold list and free list is not explicitly tracked
    // as their content are covered by the memory usage reported from the NodeidStore (array store).
    return result;
}

void
HnswNodeidMapping::compact_worst(const vespalib::datastore::CompactionStrategy& compaction_strategy)
{
    auto compacting_buffers = _nodeids.start_compact_worst_buffers(compaction_strategy);
    auto filter = compacting_buffers->make_entry_ref_filter();
   vespalib::ArrayRef<EntryRef> refs(&_refs[0], _refs.size());
   for (auto& ref : refs) {
       if (ref.valid() && filter.has(ref)) {
           ref = _nodeids.move_on_compact(ref);
       }
   }
   compacting_buffers->finish();
}

}
