// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "distance_function.h"
#include "hnsw_index.h"
#include "hnsw_index_loader.h"
#include "hnsw_index_saver.h"
#include "random_level_generator.h"
#include <vespa/searchlib/util/state_explorer_utils.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/util/rcuvector.hpp>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.hnsw_index");

namespace search::tensor {

using search::StateExplorerUtils;
using vespalib::datastore::EntryRef;

namespace {

// TODO: Move this to MemoryAllocator, with name PAGE_SIZE.
constexpr size_t small_page_size = 4_Ki;
constexpr size_t min_num_arrays_for_new_buffer = 8_Ki;
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

struct PairDist {
    uint32_t id_first;
    uint32_t id_second;
    double distance;
    PairDist(uint32_t i1, uint32_t i2, double d) noexcept
      : id_first(i1), id_second(i2), distance(d)
    {}
};
bool operator< (const PairDist &a, const PairDist &b) {
    return (a.distance < b.distance);
}

}

vespalib::datastore::ArrayStoreConfig
HnswIndex::make_default_node_store_config()
{
    return NodeStore::optimizedConfigForHugePage(max_level_array_size, vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                 small_page_size, min_num_arrays_for_new_buffer, alloc_grow_factor).enable_free_lists(true);
}

vespalib::datastore::ArrayStoreConfig
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

bool
HnswIndex::have_closer_distance(HnswCandidate candidate, const HnswCandidateVector& result) const
{
    for (const auto & neighbor : result) {
        double dist = calc_distance(candidate.docid, neighbor.docid);
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
            result.used.push_back(candidate);
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
    NearestPriQ nearest;
    for (const auto& entry : neighbors) {
        nearest.push(entry);
    }
    while (!nearest.empty()) {
        auto candidate = nearest.top();
        nearest.pop();
        if (have_closer_distance(candidate, result.used)) {
            result.unused.push_back(candidate.docid);
            continue;
        }
        result.used.push_back(candidate);
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
    auto old_links = _graph.get_link_array(docid, level);
    uint32_t max_links = max_links_for_level(level);
    if (old_links.size() > max_links) {
        HnswCandidateVector neighbors;
        for (uint32_t neighbor_docid : old_links) {
            double dist = calc_distance(docid, neighbor_docid);
            neighbors.emplace_back(neighbor_docid, dist);
        }
        auto split = select_neighbors(neighbors, max_links);
        LinkArray new_links;
        new_links.reserve(split.used.size());
        for (const auto & neighbor : split.used) {
            new_links.push_back(neighbor.docid);
        }
        _graph.set_link_array(docid, level, new_links);
        for (uint32_t removed_docid : split.unused) {
            remove_link_to(removed_docid, docid, level);
        }
    }
}

void
HnswIndex::connect_new_node(uint32_t docid, const LinkArrayRef &neighbors, uint32_t level)
{
    _graph.set_link_array(docid, level, neighbors);
    for (uint32_t neighbor_docid : neighbors) {
        auto old_links = _graph.get_link_array(neighbor_docid, level);
        add_link_to(neighbor_docid, level, old_links, docid);
    }
    for (uint32_t neighbor_docid : neighbors) {
        shrink_if_needed(neighbor_docid, level);
    }
}

void
HnswIndex::remove_link_to(uint32_t remove_from, uint32_t remove_id, uint32_t level)
{
    LinkArray new_links;
    auto old_links = _graph.get_link_array(remove_from, level);
    for (uint32_t id : old_links) {
        if (id != remove_id) new_links.push_back(id);
    }
    _graph.set_link_array(remove_from, level, new_links);
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
        for (uint32_t neighbor_docid : _graph.get_link_array(nearest.node_ref, level)) {
            auto neighbor_ref = _graph.get_node_ref(neighbor_docid);
            double dist = calc_distance(input, neighbor_docid);
            if (_graph.still_valid(neighbor_docid, neighbor_ref)
                && dist < nearest.distance)
            {
                nearest = HnswCandidate(neighbor_docid, neighbor_ref, dist);
                keep_searching = true;
            }
        }
    }
    return nearest;
}

void
HnswIndex::search_layer(const TypedCells& input, uint32_t neighbors_to_find,
                        FurthestPriQ& best_neighbors, uint32_t level, const search::BitVector *filter) const
{
    NearestPriQ candidates;
    uint32_t doc_id_limit = _graph.node_refs.size();
    if (filter) {
        doc_id_limit = std::min(filter->size(), doc_id_limit);
    }
    auto visited = _visited_set_pool.get(doc_id_limit);
    for (const auto &entry : best_neighbors.peek()) {
        if (entry.docid >= doc_id_limit) {
            continue;
        }
        candidates.push(entry);
        visited.mark(entry.docid);
        if (filter && !filter->testBit(entry.docid)) {
            assert(best_neighbors.size() == 1);
            best_neighbors.pop();
        }
    }
    double limit_dist = std::numeric_limits<double>::max();

    while (!candidates.empty()) {
        auto cand = candidates.top();
        if (cand.distance > limit_dist) {
            break;
        }
        candidates.pop();
        for (uint32_t neighbor_docid : _graph.get_link_array(cand.node_ref, level)) {
            auto neighbor_ref = _graph.get_node_ref(neighbor_docid);
            if ((! neighbor_ref.valid())
                || (neighbor_docid >= doc_id_limit)
                || visited.is_marked(neighbor_docid))
            {
                continue;
            }
            visited.mark(neighbor_docid);
            double dist_to_input = calc_distance(input, neighbor_docid);
            if (dist_to_input < limit_dist) {
                candidates.emplace(neighbor_docid, neighbor_ref, dist_to_input);
                if ((!filter) || filter->testBit(neighbor_docid)) {
                    best_neighbors.emplace(neighbor_docid, neighbor_ref, dist_to_input);
                    if (best_neighbors.size() > neighbors_to_find) {
                        best_neighbors.pop();
                        limit_dist = best_neighbors.top().distance;
                    }
                }
            }
        }
    }
}

HnswIndex::HnswIndex(const DocVectorAccess& vectors, DistanceFunction::UP distance_func,
                     RandomLevelGenerator::UP level_generator, const Config& cfg)
    :
      _graph(),
      _vectors(vectors),
      _distance_func(std::move(distance_func)),
      _level_generator(std::move(level_generator)),
      _cfg(cfg)
{
    assert(_distance_func);
}

HnswIndex::~HnswIndex() = default;

void
HnswIndex::add_document(uint32_t docid)
{
    vespalib::GenerationHandler::Guard no_guard_needed;
    PreparedAddDoc op = internal_prepare_add(docid, get_vector(docid), no_guard_needed);
    internal_complete_add(docid, op);
}

HnswIndex::PreparedAddDoc
HnswIndex::internal_prepare_add(uint32_t docid, TypedCells input_vector, vespalib::GenerationHandler::Guard read_guard) const
{
    // TODO: Add capping on num_levels
    int level = _level_generator->max_level();
    PreparedAddDoc op(docid, level, std::move(read_guard));
    auto entry = _graph.get_entry_node();
    if (entry.docid == 0) {
        // graph has no entry point
        return op;
    }
    int search_level = entry.level;
    double entry_dist = calc_distance(input_vector, entry.docid);
    // TODO: check if entry docid/node_ref is still valid here
    HnswCandidate entry_point(entry.docid, entry.node_ref, entry_dist);
    while (search_level > op.max_level) {
        entry_point = find_nearest_in_layer(input_vector, entry_point, search_level);
        --search_level;
    }

    FurthestPriQ best_neighbors;
    best_neighbors.push(entry_point);
    search_level = std::min(op.max_level, search_level);

    // Find neighbors of the added document in each level it should exist in.
    while (search_level >= 0) {
        search_layer(input_vector, _cfg.neighbors_to_explore_at_construction(), best_neighbors, search_level);
        auto neighbors = select_neighbors(best_neighbors.peek(), _cfg.max_links_on_inserts());
        op.connections[search_level].reserve(neighbors.used.size());
        for (const auto & neighbor : neighbors.used) {
            auto neighbor_levels = _graph.get_level_array(neighbor.node_ref);
            if (size_t(search_level) < neighbor_levels.size()) {
                op.connections[search_level].emplace_back(neighbor.docid, neighbor.node_ref);
            } else {
                LOG(warning, "in prepare_add(%u), selected neighbor %u is missing level %d (has %zu levels)",
                    docid, neighbor.docid, search_level, neighbor_levels.size());
            }
        }
        --search_level;
    }
    return op;
}

HnswIndex::LinkArray 
HnswIndex::filter_valid_docids(uint32_t level, const PreparedAddDoc::Links &neighbors, uint32_t self_docid)
{
    LinkArray valid;
    valid.reserve(neighbors.size());
    for (const auto & neighbor : neighbors) {
        uint32_t docid = neighbor.first;
        HnswGraph::NodeRef node_ref = neighbor.second;
        if (_graph.still_valid(docid, node_ref)) {
            assert(docid != self_docid);
            auto levels = _graph.get_level_array(node_ref);
            if (level < levels.size()) {
                valid.push_back(docid);     
            }
        }
    }
    return valid;
}

void
HnswIndex::internal_complete_add(uint32_t docid, PreparedAddDoc &op)
{
    auto node_ref = _graph.make_node_for_document(docid, op.max_level + 1);
    for (int level = 0; level <= op.max_level; ++level) {
        auto neighbors = filter_valid_docids(level, op.connections[level], docid);
        connect_new_node(docid, neighbors, level);
    }
    if (op.max_level > get_entry_level()) {
        _graph.set_entry_node({docid, node_ref, op.max_level});
    }
}

std::unique_ptr<PrepareResult>
HnswIndex::prepare_add_document(uint32_t docid, 
            TypedCells vector,
            vespalib::GenerationHandler::Guard read_guard) const
{
    uint32_t max_nodes = _graph.node_refs.size();
    if (max_nodes < _cfg.min_size_before_two_phase()) {
        // the first documents added will do all work in write thread
        // to ensure they are linked together:
        return std::unique_ptr<PrepareResult>();
    }
    PreparedAddDoc op = internal_prepare_add(docid, vector, std::move(read_guard));
    return std::make_unique<PreparedAddDoc>(std::move(op));
}

void
HnswIndex::complete_add_document(uint32_t docid, std::unique_ptr<PrepareResult> prepare_result)
{
    auto prepared = dynamic_cast<PreparedAddDoc *>(prepare_result.get());
    if (prepared && (prepared->docid == docid)) {
        internal_complete_add(docid, *prepared);
    } else {
        // we expect this for the first documents added, so no warning for them
        if (_graph.node_refs.size() > 1.25 * _cfg.min_size_before_two_phase()) {
            LOG(warning, "complete_add_document(%u) called with invalid prepare_result %s/%u",
                docid, (prepared ? "valid ptr" : "nullptr"), (prepared ? prepared->docid : 0u));
        }
        // fallback to normal add
        add_document(docid);
    }
}

void
HnswIndex::mutual_reconnect(const LinkArrayRef &cluster, uint32_t level)
{
    std::vector<PairDist> pairs;
    for (uint32_t i = 0; i + 1 < cluster.size(); ++i) {
        uint32_t n_id_1 = cluster[i];
        LinkArrayRef n_list_1 = _graph.get_link_array(n_id_1, level);
        for (uint32_t j = i + 1; j < cluster.size(); ++j) {
            uint32_t n_id_2 = cluster[j];
            if (has_link_to(n_list_1, n_id_2)) continue;
            pairs.emplace_back(n_id_1, n_id_2, calc_distance(n_id_1, n_id_2));
        }
    }
    std::sort(pairs.begin(), pairs.end());
    for (const PairDist & pair : pairs) {
        LinkArrayRef old_links_1 = _graph.get_link_array(pair.id_first, level);
        if (old_links_1.size() >= _cfg.max_links_on_inserts()) continue;

        LinkArrayRef old_links_2 = _graph.get_link_array(pair.id_second, level);
        if (old_links_2.size() >= _cfg.max_links_on_inserts()) continue;

        add_link_to(pair.id_first, level, old_links_1, pair.id_second);
        add_link_to(pair.id_second, level, old_links_2, pair.id_first);
    }
}

void
HnswIndex::remove_document(uint32_t docid)
{
    bool need_new_entrypoint = (docid == get_entry_docid());
    LevelArrayRef node_levels = _graph.get_level_array(docid);
    for (int level = node_levels.size(); level-- > 0; ) {
        LinkArrayRef my_links = _graph.get_link_array(docid, level);
        for (uint32_t neighbor_id : my_links) {
            if (need_new_entrypoint) {
                auto entry_node_ref = _graph.get_node_ref(neighbor_id);
                _graph.set_entry_node({neighbor_id, entry_node_ref, level});
                need_new_entrypoint = false;
            }
            remove_link_to(neighbor_id, docid, level);
        }
        mutual_reconnect(my_links, level);
    }
    if (need_new_entrypoint) {
        HnswGraph::EntryNode entry;
        _graph.set_entry_node(entry);
    }
    _graph.remove_node_for_document(docid);
}

void
HnswIndex::transfer_hold_lists(generation_t current_gen)
{
    // Note: RcuVector transfers hold lists as part of reallocation based on current generation.
    //       We need to set the next generation here, as it is incremented on a higher level right after this call.
    _graph.node_refs.setGeneration(current_gen + 1);
    _graph.nodes.transferHoldLists(current_gen);
    _graph.links.transferHoldLists(current_gen);
}

void
HnswIndex::trim_hold_lists(generation_t first_used_gen)
{
    _graph.node_refs.removeOldGenerations(first_used_gen);
    _graph.nodes.trimHoldLists(first_used_gen);
    _graph.links.trimHoldLists(first_used_gen);
}

vespalib::MemoryUsage
HnswIndex::memory_usage() const
{
    vespalib::MemoryUsage result;
    result.merge(_graph.node_refs.getMemoryUsage());
    result.merge(_graph.nodes.getMemoryUsage());
    result.merge(_graph.links.getMemoryUsage());
    result.merge(_visited_set_pool.memory_usage());
    return result;
}

void
HnswIndex::get_state(const vespalib::slime::Inserter& inserter) const
{
    auto& object = inserter.insertObject();
    StateExplorerUtils::memory_usage_to_slime(memory_usage(), object.setObject("memory_usage"));
    object.setLong("nodes", _graph.size());
    auto& histogram_array = object.setArray("level_histogram");
    auto& links_hst_array = object.setArray("level_0_links_histogram");
    auto histograms = _graph.histograms();
    uint32_t valid_nodes = 0;
    for (uint32_t hist_val : histograms.level_histogram) {
        histogram_array.addLong(hist_val);
        valid_nodes += hist_val;
    }
    object.setLong("valid_nodes", valid_nodes);
    for (uint32_t hist_val : histograms.links_histogram) {
        links_hst_array.addLong(hist_val);
    }
    uint32_t reachable = count_reachable_nodes();
    uint32_t unreachable = valid_nodes - reachable;
    object.setLong("unreachable_nodes", unreachable);
    auto entry_node = _graph.get_entry_node();
    object.setLong("entry_docid", entry_node.docid);
    object.setLong("entry_level", entry_node.level);
    auto& cfgObj = object.setObject("cfg");
    cfgObj.setLong("max_links_at_level_0", _cfg.max_links_at_level_0());
    cfgObj.setLong("max_links_on_inserts", _cfg.max_links_on_inserts());
    cfgObj.setLong("neighbors_to_explore_at_construction",
                   _cfg.neighbors_to_explore_at_construction());
}

std::unique_ptr<NearestNeighborIndexSaver>
HnswIndex::make_saver() const
{
    return std::make_unique<HnswIndexSaver>(_graph);
}

bool
HnswIndex::load(const fileutil::LoadedBuffer& buf)
{
    assert(get_entry_docid() == 0); // cannot load after index has data
    HnswIndexLoader loader(_graph);
    return loader.load(buf);
}

struct NeighborsByDocId {
    bool operator() (const NearestNeighborIndex::Neighbor &lhs,
                     const NearestNeighborIndex::Neighbor &rhs)
    {
        return (lhs.docid < rhs.docid);
    }
};

std::vector<NearestNeighborIndex::Neighbor>
HnswIndex::top_k_by_docid(uint32_t k, TypedCells vector,
                          const BitVector *filter, uint32_t explore_k,
                          double distance_threshold) const
{
    std::vector<Neighbor> result;
    FurthestPriQ candidates = top_k_candidates(vector, std::max(k, explore_k), filter);
    while (candidates.size() > k) {
        candidates.pop();
    }
    result.reserve(candidates.size());
    for (const HnswCandidate & hit : candidates.peek()) {
        if (hit.distance > distance_threshold) continue;
        result.emplace_back(hit.docid, hit.distance);
    }
    std::sort(result.begin(), result.end(), NeighborsByDocId());
    return result;
}

std::vector<NearestNeighborIndex::Neighbor>
HnswIndex::find_top_k(uint32_t k, TypedCells vector, uint32_t explore_k,
                      double distance_threshold) const
{
    return top_k_by_docid(k, vector, nullptr, explore_k, distance_threshold);
}

std::vector<NearestNeighborIndex::Neighbor>
HnswIndex::find_top_k_with_filter(uint32_t k, TypedCells vector,
                                  const BitVector &filter, uint32_t explore_k,
                                  double distance_threshold) const
{
    return top_k_by_docid(k, vector, &filter, explore_k, distance_threshold);
}

FurthestPriQ
HnswIndex::top_k_candidates(const TypedCells &vector, uint32_t k, const BitVector *filter) const
{
    FurthestPriQ best_neighbors;
    auto entry = _graph.get_entry_node();
    if (entry.docid == 0) {
        // graph has no entry point
        return best_neighbors;
    }
    int search_level = entry.level;
    double entry_dist = calc_distance(vector, entry.docid);
    // TODO: check if entry docid/node_ref is still valid here
    HnswCandidate entry_point(entry.docid, entry.node_ref, entry_dist);
    while (search_level > 0) {
        entry_point = find_nearest_in_layer(vector, entry_point, search_level);
        --search_level;
    }
    best_neighbors.push(entry_point);
    search_layer(vector, k, best_neighbors, 0, filter);
    return best_neighbors;
}

HnswNode
HnswIndex::get_node(uint32_t docid) const
{
    auto node_ref = _graph.node_refs[docid].load_acquire();
    if (!node_ref.valid()) {
        return HnswNode();
    }
    auto levels = _graph.nodes.get(node_ref);
    HnswNode::LevelArray result;
    for (const auto& links_ref : levels) {
        auto links = _graph.links.get(links_ref.load_acquire());
        HnswNode::LinkArray result_links(links.begin(), links.end());
        std::sort(result_links.begin(), result_links.end());
        result.push_back(result_links);
    }
    return HnswNode(result);
}

void
HnswIndex::set_node(uint32_t docid, const HnswNode &node)
{
    size_t num_levels = node.size();
    assert(num_levels > 0);
    auto node_ref = _graph.make_node_for_document(docid, num_levels);
    for (size_t level = 0; level < num_levels; ++level) {
        connect_new_node(docid, node.level(level), level);
    }
    int max_level = num_levels - 1;
    if (get_entry_level() < max_level) {
        _graph.set_entry_node({docid, node_ref, max_level});
    }
}

bool
HnswIndex::check_link_symmetry() const
{
    bool all_sym = true;
    for (size_t docid = 0; docid < _graph.node_refs.size(); ++docid) {
        auto node_ref = _graph.node_refs[docid].load_acquire();
        if (node_ref.valid()) {
            auto levels = _graph.nodes.get(node_ref);
            uint32_t level = 0;
            for (const auto& links_ref : levels) {
                auto links = _graph.links.get(links_ref.load_acquire());
                for (auto neighbor_docid : links) {
                    auto neighbor_links = _graph.get_link_array(neighbor_docid, level);
                    if (! has_link_to(neighbor_links, docid)) {
                        all_sym = false;
                        LOG(warning, "check_link_symmetry: docid %zu links to %u on level %u, but no backlink",
                            docid, neighbor_docid, level);
                    }
                }
                ++level;
            }
        }
    }
    return all_sym;
}

uint32_t
HnswIndex::count_reachable_nodes() const
{
    auto entry = _graph.get_entry_node();
    int search_level = entry.level;
    if (search_level < 0) {
        return 0;
    }
    auto visited = _visited_set_pool.get(_graph.size());
    LinkArray found_links;
    found_links.push_back(entry.docid);
    visited.mark(entry.docid);
    while (search_level >= 0) {
        for (uint32_t idx = 0; idx < found_links.size(); ++idx) {
            uint32_t docid = found_links[idx];
            auto neighbors = _graph.get_link_array(docid, search_level);
            for (uint32_t neighbor : neighbors) {
                if (visited.is_marked(neighbor)) continue;
                visited.mark(neighbor);
                found_links.push_back(neighbor);
            }
        }
        --search_level;
    }
    return found_links.size();
}

} // namespace
