// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_index_base.h"
#include "random_level_generator.h"
#include <vespa/vespalib/datastore/array_store.hpp>

namespace search::tensor {

namespace {

// TODO: Move this to MemoryAllocator, with name PAGE_SIZE.
constexpr size_t small_page_size = 4 * 1024;
constexpr size_t min_num_arrays_for_new_buffer = 8 * 1024;
constexpr float alloc_grow_factor = 0.2;
// TODO: Adjust these numbers to what we accept as max in config.
constexpr size_t max_level_array_size = 16;
constexpr size_t max_link_array_size = 64;

}

search::datastore::ArrayStoreConfig
HnswIndexBase::make_default_node_store_config()
{
    return NodeStore::optimizedConfigForHugePage(max_level_array_size, vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                 small_page_size, min_num_arrays_for_new_buffer, alloc_grow_factor).enable_free_lists(true);
}

search::datastore::ArrayStoreConfig
HnswIndexBase::make_default_link_store_config()
{
    return LinkStore::optimizedConfigForHugePage(max_link_array_size, vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                 small_page_size, min_num_arrays_for_new_buffer, alloc_grow_factor).enable_free_lists(true);
}

uint32_t
HnswIndexBase::max_links_for_level(uint32_t level) const
{
    return (level == 0) ? _cfg.max_links_at_level_0() : _cfg.max_links_at_hierarchic_levels();
}

uint32_t
HnswIndexBase::make_node_for_document(uint32_t docid)
{
    uint32_t max_level = _level_generator.max_level();
    // TODO: Add capping on num_levels
    uint32_t num_levels = max_level + 1;
    // Note: The level array instance lives as long as the document is present in the index.
    LevelArray levels(num_levels, EntryRef());
    auto node_ref = _nodes.add(levels);
    // TODO: Add memory barrier?
    _node_refs[docid] = node_ref;
    return max_level;
}

HnswIndexBase::LevelArrayRef
HnswIndexBase::get_level_array(uint32_t docid) const
{
    // TODO: Add memory barrier?
    auto node_ref = _node_refs[docid];
    return _nodes.get(node_ref);
}

HnswIndexBase::LinkArrayRef
HnswIndexBase::get_link_array(uint32_t docid, uint32_t level) const
{
    auto levels = get_level_array(docid);
    assert(level < levels.size());
    return _links.get(levels[level]);
}

void
HnswIndexBase::set_link_array(uint32_t docid, uint32_t level, const LinkArrayRef& links)
{
    auto links_ref = _links.add(links);
    // TODO: Add memory barrier?
    auto node_ref = _node_refs[docid];
    auto levels = _nodes.get_writable(node_ref);
    // TODO: Make this change atomic.
    levels[level] = links_ref;
}

bool
HnswIndexBase::have_closer_distance(HnswCandidate candidate, const LinkArray& result) const
{
    for (uint32_t result_docid : result) {
        double dist = calc_distance(candidate.docid, result_docid);
        if (dist < candidate.distance) {
            return true;
        }
    }
    return false;
}

HnswIndexBase::LinkArray
HnswIndexBase::select_neighbors_simple(const HnswCandidateVector& neighbors, uint32_t max_links) const
{
    HnswCandidateVector sorted(neighbors);
    std::sort(sorted.begin(), sorted.end(), LesserDistance());
    LinkArray result;
    for (size_t i = 0, m = std::min(static_cast<size_t>(max_links), sorted.size()); i < m; ++i) {
        result.push_back(sorted[i].docid);
    }
    return result;
}

HnswIndexBase::LinkArray
HnswIndexBase::select_neighbors_heuristic(const HnswCandidateVector& neighbors, uint32_t max_links) const
{
    LinkArray result;
    bool need_filtering = neighbors.size() > max_links;
    NearestPriQ nearest;
    for (const auto& entry : neighbors) {
        nearest.push(entry);
    }
    while (!nearest.empty()) {
        auto candidate = nearest.top();
        nearest.pop();
        if (need_filtering && have_closer_distance(candidate, result)) {
            continue;
        }
        result.push_back(candidate.docid);
        if (result.size() == max_links) {
            return result;
        }
    }
    return result;
}

HnswIndexBase::LinkArray
HnswIndexBase::select_neighbors(const HnswCandidateVector& neighbors, uint32_t max_links) const
{
    if (_cfg.heuristic_select_neighbors()) {
        return select_neighbors_heuristic(neighbors, max_links);
    } else {
        return select_neighbors_simple(neighbors, max_links);
    }
}

void
HnswIndexBase::connect_new_node(uint32_t docid, const LinkArray& neighbors, uint32_t level)
{
    set_link_array(docid, level, neighbors);
    for (uint32_t neighbor_docid : neighbors) {
        auto old_links = get_link_array(neighbor_docid, level);
        LinkArray new_links(old_links.begin(), old_links.end());
        new_links.push_back(docid);
        set_link_array(neighbor_docid, level, new_links);
    }
}

HnswIndexBase::HnswIndexBase(const DocVectorAccess& vectors, RandomLevelGenerator& level_generator, const Config& cfg)
    : _vectors(vectors),
      _level_generator(level_generator),
      _cfg(cfg),
      _node_refs(),
      _nodes(make_default_node_store_config()),
      _links(make_default_link_store_config()),
      _entry_docid(0), // Note that docid 0 is reserved and never used
      _entry_level(-1)
{
}

HnswIndexBase::~HnswIndexBase() = default;

HnswNode
HnswIndexBase::get_node(uint32_t docid) const
{
    auto node_ref = _node_refs[docid];
    if (!node_ref.valid()) {
        return HnswNode();
    }
    auto levels = _nodes.get(node_ref);
    HnswNode::LevelArray result;
    for (const auto& links_ref : levels) {
        auto links = _links.get(links_ref);
        HnswNode::LinkArray result_links(links.begin(), links.end());
        std::sort(result_links.begin(), result_links.end());
        result.push_back(result_links);
    }
    return HnswNode(result);
}

}

