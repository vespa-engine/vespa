// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_function.h"
#include "hnsw_index.h"
#include "random_level_generator.h"
#include <vespa/eval/tensor/dense/typed_cells.h>
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/util/rcuvector.hpp>

namespace search::tensor {

using search::datastore::EntryRef;

namespace {

// TODO: Move this to MemoryAllocator, with name PAGE_SIZE.
constexpr size_t small_page_size = 4 * 1024;
constexpr size_t min_num_arrays_for_new_buffer = 8 * 1024;
constexpr float alloc_grow_factor = 0.2;
// TODO: Adjust these numbers to what we accept as max in config.
constexpr size_t max_level_array_size = 16;
constexpr size_t max_link_array_size = 64;

bool has_link_to(vespalib::ConstArrayRef<uint32_t> links, uint32_t id) {
    for (uint32_t link : links) {
        if (link == id) return true;
    }
    return false;
}

}

search::datastore::ArrayStoreConfig
HnswIndex::make_default_node_store_config()
{
    return NodeStore::optimizedConfigForHugePage(max_level_array_size, vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                 small_page_size, min_num_arrays_for_new_buffer, alloc_grow_factor).enable_free_lists(true);
}

search::datastore::ArrayStoreConfig
HnswIndex::make_default_link_store_config()
{
    return LinkStore::optimizedConfigForHugePage(max_link_array_size, vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                 small_page_size, min_num_arrays_for_new_buffer, alloc_grow_factor).enable_free_lists(true);
}

uint32_t
HnswIndex::max_links_for_level(uint32_t level) const
{
    return (level == 0) ? _cfg.max_links_at_level_0() : _cfg.max_links_on_inserts();
}

uint32_t
HnswIndex::make_node_for_document(uint32_t docid)
{
    // A document cannot be added twice.
    assert(!_node_refs[docid].load_acquire().valid());

    uint32_t max_level = _level_generator->max_level();
    // TODO: Add capping on num_levels
    uint32_t num_levels = max_level + 1;
    // Note: The level array instance lives as long as the document is present in the index.
    LevelArray levels(num_levels, AtomicEntryRef());
    auto node_ref = _nodes.add(levels);
    _node_refs[docid].store_release(node_ref);
    return max_level;
}

void
HnswIndex::remove_node_for_document(uint32_t docid)
{
    auto node_ref = _node_refs[docid].load_acquire();
    _nodes.remove(node_ref);
    EntryRef invalid;
    _node_refs[docid].store_release(invalid);
}

HnswIndex::LevelArrayRef
HnswIndex::get_level_array(uint32_t docid) const
{
    auto node_ref = _node_refs[docid].load_acquire();
    return _nodes.get(node_ref);
}

HnswIndex::LinkArrayRef
HnswIndex::get_link_array(uint32_t docid, uint32_t level) const
{
    auto levels = get_level_array(docid);
    assert(level < levels.size());
    return _links.get(levels[level].load_acquire());
}

void
HnswIndex::set_link_array(uint32_t docid, uint32_t level, const LinkArrayRef& links)
{
    auto new_links_ref = _links.add(links);
    auto node_ref = _node_refs[docid].load_acquire();
    auto levels = _nodes.get_writable(node_ref);
    auto old_links_ref = levels[level].load_acquire();
    levels[level].store_release(new_links_ref);
    _links.remove(old_links_ref);
}

bool
HnswIndex::have_closer_distance(HnswCandidate candidate, const LinkArray& result) const
{
    for (uint32_t result_docid : result) {
        double dist = calc_distance(candidate.docid, result_docid);
        if (dist < candidate.distance) {
            return true;
        }
    }
    return false;
}

HnswIndex::SelectResult
HnswIndex::select_neighbors_simple(const HnswCandidateVector& neighbors, uint32_t max_links) const
{
    HnswCandidateVector sorted(neighbors);
    std::sort(sorted.begin(), sorted.end(), LesserDistance());
    SelectResult result;
    for (const auto & candidate : sorted) {
        if (result.used.size() < max_links) {
            result.used.push_back(candidate.docid);
        } else {
            result.unused.push_back(candidate.docid);
        }
    }
    return result;
}

HnswIndex::SelectResult
HnswIndex::select_neighbors_heuristic(const HnswCandidateVector& neighbors, uint32_t max_links) const
{
    SelectResult result;
    bool need_filtering = neighbors.size() > max_links;
    NearestPriQ nearest;
    for (const auto& entry : neighbors) {
        nearest.push(entry);
    }
    while (!nearest.empty()) {
        auto candidate = nearest.top();
        nearest.pop();
        if (need_filtering && have_closer_distance(candidate, result.used)) {
            result.unused.push_back(candidate.docid);
            continue;
        }
        result.used.push_back(candidate.docid);
        if (result.used.size() == max_links) {
            while (!nearest.empty()) {
                candidate = nearest.top();
                nearest.pop();
                result.unused.push_back(candidate.docid);
            }
        }
    }
    return result;
}

HnswIndex::SelectResult
HnswIndex::select_neighbors(const HnswCandidateVector& neighbors, uint32_t max_links) const
{
    if (_cfg.heuristic_select_neighbors()) {
        return select_neighbors_heuristic(neighbors, max_links);
    } else {
        return select_neighbors_simple(neighbors, max_links);
    }
}

void
HnswIndex::shrink_if_needed(uint32_t docid, uint32_t level)
{
    auto old_links = get_link_array(docid, level);
    uint32_t max_links = max_links_for_level(level);
    if (old_links.size() > max_links) {
        HnswCandidateVector neighbors;
        for (uint32_t neighbor_docid : old_links) {
            double dist = calc_distance(docid, neighbor_docid);
            neighbors.emplace_back(neighbor_docid, dist);
        }
        auto split = select_neighbors(neighbors, max_links);
        set_link_array(docid, level, split.used);
        for (uint32_t removed_docid : split.unused) {
            remove_link_to(removed_docid, docid, level);
        }
    }
}

void
HnswIndex::connect_new_node(uint32_t docid, const LinkArrayRef &neighbors, uint32_t level)
{
    set_link_array(docid, level, neighbors);
    for (uint32_t neighbor_docid : neighbors) {
        auto old_links = get_link_array(neighbor_docid, level);
        LinkArray new_links(old_links.begin(), old_links.end());
        new_links.push_back(docid);
        set_link_array(neighbor_docid, level, new_links);
    }
    for (uint32_t neighbor_docid : neighbors) {
        shrink_if_needed(neighbor_docid, level);
    }
}

void
HnswIndex::remove_link_to(uint32_t remove_from, uint32_t remove_id, uint32_t level)
{
    LinkArray new_links;
    auto old_links = get_link_array(remove_from, level);
    for (uint32_t id : old_links) {
        if (id != remove_id) new_links.push_back(id);
    }
    set_link_array(remove_from, level, new_links);
}


double
HnswIndex::calc_distance(uint32_t lhs_docid, uint32_t rhs_docid) const
{
    auto lhs = get_vector(lhs_docid);
    return calc_distance(lhs, rhs_docid);
}

double
HnswIndex::calc_distance(const TypedCells& lhs, uint32_t rhs_docid) const
{
    auto rhs = get_vector(rhs_docid);
    return _distance_func->calc(lhs, rhs);
}

HnswCandidate
HnswIndex::find_nearest_in_layer(const TypedCells& input, const HnswCandidate& entry_point, uint32_t level) const
{
    HnswCandidate nearest = entry_point;
    bool keep_searching = true;
    while (keep_searching) {
        keep_searching = false;
        for (uint32_t neighbor_docid : get_link_array(nearest.docid, level)) {
            double dist = calc_distance(input, neighbor_docid);
            if (dist < nearest.distance) {
                nearest = HnswCandidate(neighbor_docid, dist);
                keep_searching = true;
            }
        }
    }
    return nearest;
}

void
HnswIndex::search_layer(const TypedCells& input, uint32_t neighbors_to_find, FurthestPriQ& best_neighbors, uint32_t level) const
{
    NearestPriQ candidates;
    // TODO: Add proper handling of visited set.
    auto visited = BitVector::create(_node_refs.size());
    for (const auto &entry : best_neighbors.peek()) {
        candidates.push(entry);
        visited->setBit(entry.docid);
    }
    double limit_dist = std::numeric_limits<double>::max();

    while (!candidates.empty()) {
        auto cand = candidates.top();
        if (cand.distance > limit_dist) {
            break;
        }
        candidates.pop();
        for (uint32_t neighbor_docid : get_link_array(cand.docid, level)) {
            if (visited->testBit(neighbor_docid)) {
                continue;
            }
            visited->setBit(neighbor_docid);
            double dist_to_input = calc_distance(input, neighbor_docid);
            if (dist_to_input < limit_dist) {
                candidates.emplace(neighbor_docid, dist_to_input);
                best_neighbors.emplace(neighbor_docid, dist_to_input);
                if (best_neighbors.size() > neighbors_to_find) {
                    best_neighbors.pop();
                    limit_dist = best_neighbors.top().distance;
                }
            }
        }
    }
}

HnswIndex::HnswIndex(const DocVectorAccess& vectors, DistanceFunction::UP distance_func,
                     RandomLevelGenerator::UP level_generator, const Config& cfg)
    : _vectors(vectors),
      _distance_func(std::move(distance_func)),
      _level_generator(std::move(level_generator)),
      _cfg(cfg),
      _node_refs(),
      _nodes(make_default_node_store_config()),
      _links(make_default_link_store_config()),
      _entry_docid(0), // Note that docid 0 is reserved and never used
      _entry_level(-1)
{
}

HnswIndex::~HnswIndex() = default;

void
HnswIndex::add_document(uint32_t docid)
{
    auto input = get_vector(docid);
    _node_refs.ensure_size(docid + 1, AtomicEntryRef());
    int level = make_node_for_document(docid);
    if (_entry_docid == 0) {
        _entry_docid = docid;
        _entry_level = level;
        return;
    }

    int search_level = _entry_level;
    double entry_dist = calc_distance(input, _entry_docid);
    HnswCandidate entry_point(_entry_docid, entry_dist);
    while (search_level > level) {
        entry_point = find_nearest_in_layer(input, entry_point, search_level);
        --search_level;
    }

    FurthestPriQ best_neighbors;
    best_neighbors.push(entry_point);
    search_level = std::min(level, _entry_level);

    // Insert the added document in each level it should exist in.
    while (search_level >= 0) {
        // TODO: Rename to search_level?
        search_layer(input, _cfg.neighbors_to_explore_at_construction(), best_neighbors, search_level);
        auto neighbors = select_neighbors(best_neighbors.peek(), _cfg.max_links_on_inserts());
        connect_new_node(docid, neighbors.used, search_level);
        // TODO: Shrink neighbors if needed
        --search_level;
    }
    if (level > _entry_level) {
        _entry_docid = docid;
        _entry_level = level;
    }
}

void
HnswIndex::remove_document(uint32_t docid)
{
    bool need_new_entrypoint = (docid == _entry_docid);
    LinkArray empty;
    LevelArrayRef node_levels = get_level_array(docid);
    for (int level = node_levels.size(); level-- > 0; ) {
        LinkArrayRef my_links = get_link_array(docid, level);
        for (uint32_t neighbor_id : my_links) {
            if (need_new_entrypoint) {
                _entry_docid = neighbor_id;
                _entry_level = level;
                need_new_entrypoint = false;
            }
            remove_link_to(neighbor_id, docid, level);
        }
        set_link_array(docid, level, empty);
    }
    if (need_new_entrypoint) {
        _entry_docid = 0;
        _entry_level = -1;
    }
    remove_node_for_document(docid);
}

void
HnswIndex::transfer_hold_lists(generation_t current_gen)
{
    // Note: RcuVector transfers hold lists as part of reallocation based on current generation.
    //       We need to set the next generation here, as it is incremented on a higher level right after this call.
    _node_refs.setGeneration(current_gen + 1);
    _nodes.transferHoldLists(current_gen);
    _links.transferHoldLists(current_gen);
}

void
HnswIndex::trim_hold_lists(generation_t first_used_gen)
{
    _node_refs.removeOldGenerations(first_used_gen);
    _nodes.trimHoldLists(first_used_gen);
    _links.trimHoldLists(first_used_gen);
}

vespalib::MemoryUsage
HnswIndex::memory_usage() const
{
    vespalib::MemoryUsage result;
    result.merge(_node_refs.getMemoryUsage());
    result.merge(_nodes.getMemoryUsage());
    result.merge(_links.getMemoryUsage());
    return result;
}

struct NeighborsByDocId {
    bool operator() (const NearestNeighborIndex::Neighbor &lhs,
                     const NearestNeighborIndex::Neighbor &rhs)
    {
        return (lhs.docid < rhs.docid);
    }
};

std::vector<NearestNeighborIndex::Neighbor>
HnswIndex::find_top_k(uint32_t k, TypedCells vector, uint32_t explore_k) const
{
    std::vector<Neighbor> result;
    FurthestPriQ candidates = top_k_candidates(vector, std::max(k, explore_k));
    while (candidates.size() > k) {
        candidates.pop();
    }
    result.reserve(candidates.size());
    for (const HnswCandidate & hit : candidates.peek()) {
        result.emplace_back(hit.docid, hit.distance);
    }
    std::sort(result.begin(), result.end(), NeighborsByDocId());
    return result;
}

FurthestPriQ
HnswIndex::top_k_candidates(const TypedCells &vector, uint32_t k) const
{
    FurthestPriQ best_neighbors;
    if (_entry_level < 0) {
        return best_neighbors;
    }
    double entry_dist = calc_distance(vector, _entry_docid);
    HnswCandidate entry_point(_entry_docid, entry_dist);
    int search_level = _entry_level;
    while (search_level > 0) {
        entry_point = find_nearest_in_layer(vector, entry_point, search_level);
        --search_level;
    }
    best_neighbors.push(entry_point);
    search_layer(vector, k, best_neighbors, 0);
    return best_neighbors;
}

HnswNode
HnswIndex::get_node(uint32_t docid) const
{
    auto node_ref = _node_refs[docid].load_acquire();
    if (!node_ref.valid()) {
        return HnswNode();
    }
    auto levels = _nodes.get(node_ref);
    HnswNode::LevelArray result;
    for (const auto& links_ref : levels) {
        auto links = _links.get(links_ref.load_acquire());
        HnswNode::LinkArray result_links(links.begin(), links.end());
        std::sort(result_links.begin(), result_links.end());
        result.push_back(result_links);
    }
    return HnswNode(result);
}

void
HnswIndex::set_node(uint32_t docid, const HnswNode &node)
{
    _node_refs.ensure_size(docid + 1, AtomicEntryRef());
    // A document cannot be added twice.
    assert(!_node_refs[docid].load_acquire().valid());

    // make new node
    size_t num_levels = node.size();
    assert(num_levels > 0);
    LevelArray levels(num_levels, AtomicEntryRef());
    auto node_ref = _nodes.add(levels);
    _node_refs[docid].store_release(node_ref);

    for (size_t level = 0; level < num_levels; ++level) {
        connect_new_node(docid, node.level(level), level);
    }
    int max_level = num_levels - 1;
    if (_entry_level < max_level) {
        _entry_docid = docid;
        _entry_level = max_level;
    }
}

bool
HnswIndex::check_link_symmetry() const
{
    bool all_sym = true;
    for (size_t docid = 0; docid < _node_refs.size(); ++docid) {
        auto node_ref = _node_refs[docid].load_acquire();
        if (node_ref.valid()) {
            auto levels = _nodes.get(node_ref);
            uint32_t level = 0;
            for (const auto& links_ref : levels) {
                auto links = _links.get(links_ref.load_acquire());
                for (auto neighbor_docid : links) {
                    auto neighbor_links = get_link_array(neighbor_docid, level);
                    if (! has_link_to(neighbor_links, docid)) {
                        all_sym = false;
                    }
                }
                ++level;
            }
        }
    }
    return all_sym;
}

} // namespace
