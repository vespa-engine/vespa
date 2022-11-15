// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_index.h"
#include "bitvector_visited_tracker.h"
#include "distance_function.h"
#include "hash_set_visited_tracker.h"
#include "hnsw_index_loader.hpp"
#include "hnsw_index_saver.h"
#include "random_level_generator.h"
#include "vector_bundle.h"
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/searchlib/attribute/address_space_usage.h>
#include <vespa/searchlib/queryeval/global_filter.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/searchlib/util/state_explorer_utils.h>
#include <vespa/vespalib/data/slime/cursor.h>
#include <vespa/vespalib/data/slime/inserter.h>
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/datastore/compaction_strategy.h>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.hnsw_index");

namespace search::tensor {

using search::AddressSpaceComponents;
using search::StateExplorerUtils;
using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;

namespace {

// TODO: Move this to MemoryAllocator, with name PAGE_SIZE.
constexpr size_t small_page_size = 4_Ki;
constexpr size_t min_num_arrays_for_new_buffer = 512_Ki;
constexpr float alloc_grow_factor = 0.3;
// TODO: Adjust these numbers to what we accept as max in config.
constexpr size_t max_level_array_size = 16;
constexpr size_t max_link_array_size = 193;
constexpr vespalib::duration MAX_COUNT_DURATION(100ms);

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
        double dist = calc_distance(candidate.nodeid, neighbor.nodeid);
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
            result.unused.push_back(candidate.nodeid);
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
            result.unused.push_back(candidate.nodeid);
            continue;
        }
        result.used.push_back(candidate);
        if (result.used.size() == max_links) {
            while (!nearest.empty()) {
                candidate = nearest.top();
                nearest.pop();
                result.unused.push_back(candidate.nodeid);
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
HnswIndex::shrink_if_needed(uint32_t nodeid, uint32_t level)
{
    auto old_links = _graph.get_link_array(nodeid, level);
    uint32_t max_links = max_links_for_level(level);
    if (old_links.size() > max_links) {
        HnswCandidateVector neighbors;
        neighbors.reserve(old_links.size());
        for (uint32_t neighbor_nodeid : old_links) {
            double dist = calc_distance(nodeid, neighbor_nodeid);
            neighbors.emplace_back(neighbor_nodeid, dist);
        }
        auto split = select_neighbors(neighbors, max_links);
        LinkArray new_links;
        new_links.reserve(split.used.size());
        for (const auto & neighbor : split.used) {
            new_links.push_back(neighbor.nodeid);
        }
        _graph.set_link_array(nodeid, level, new_links);
        for (uint32_t removed_nodeid : split.unused) {
            remove_link_to(removed_nodeid, nodeid, level);
        }
    }
}

void
HnswIndex::connect_new_node(uint32_t nodeid, const LinkArrayRef &neighbors, uint32_t level)
{
    _graph.set_link_array(nodeid, level, neighbors);
    for (uint32_t neighbor_nodeid : neighbors) {
        auto old_links = _graph.get_link_array(neighbor_nodeid, level);
        add_link_to(neighbor_nodeid, level, old_links, nodeid);
    }
    for (uint32_t neighbor_nodeid : neighbors) {
        shrink_if_needed(neighbor_nodeid, level);
    }
}

void
HnswIndex::remove_link_to(uint32_t remove_from, uint32_t remove_id, uint32_t level)
{
    LinkArray new_links;
    auto old_links = _graph.get_link_array(remove_from, level);
    new_links.reserve(old_links.size());
    for (uint32_t id : old_links) {
        if (id != remove_id) new_links.push_back(id);
    }
    _graph.set_link_array(remove_from, level, new_links);
}


double
HnswIndex::calc_distance(uint32_t lhs_nodeid, uint32_t rhs_nodeid) const
{
    auto lhs = get_vector(lhs_nodeid);
    return calc_distance(lhs, rhs_nodeid);
}

double
HnswIndex::calc_distance(const TypedCells& lhs, uint32_t rhs_nodeid) const
{
    auto rhs = get_vector(rhs_nodeid);
    return _distance_func->calc(lhs, rhs);
}

uint32_t
HnswIndex::estimate_visited_nodes(uint32_t level, uint32_t nodeid_limit, uint32_t neighbors_to_find, const GlobalFilter* filter) const
{
    uint32_t m_for_level = max_links_for_level(level);
    uint64_t base_estimate = uint64_t(m_for_level) * neighbors_to_find + 100;
    if (base_estimate >= nodeid_limit) {
        return nodeid_limit;
    }
    if (!filter) {
        return base_estimate;
    }
    uint32_t true_bits = filter->count();
    if (true_bits == 0) {
        return nodeid_limit;
    }
    double scaler = double(filter->size()) / true_bits;
    double scaled_estimate = scaler * base_estimate;
    if (scaled_estimate >= nodeid_limit) {
        return nodeid_limit;
    }
    return scaled_estimate;
}

HnswCandidate
HnswIndex::find_nearest_in_layer(const TypedCells& input, const HnswCandidate& entry_point, uint32_t level) const
{
    HnswCandidate nearest = entry_point;
    bool keep_searching = true;
    while (keep_searching) {
        keep_searching = false;
        for (uint32_t neighbor_nodeid : _graph.get_link_array(nearest.node_ref, level)) {
            auto neighbor_ref = _graph.acquire_node_ref(neighbor_nodeid);
            double dist = calc_distance(input, neighbor_nodeid);
            if (_graph.still_valid(neighbor_nodeid, neighbor_ref)
                && dist < nearest.distance)
            {
                nearest = HnswCandidate(neighbor_nodeid, neighbor_ref, dist);
                keep_searching = true;
            }
        }
    }
    return nearest;
}

template <class VisitedTracker>
void
HnswIndex::search_layer_helper(const TypedCells& input, uint32_t neighbors_to_find,
                               FurthestPriQ& best_neighbors, uint32_t level, const GlobalFilter *filter,
                               uint32_t nodeid_limit, uint32_t estimated_visited_nodes) const
{
    NearestPriQ candidates;
    VisitedTracker visited(nodeid_limit, estimated_visited_nodes);
    for (const auto &entry : best_neighbors.peek()) {
        if (entry.nodeid >= nodeid_limit) {
            continue;
        }
        candidates.push(entry);
        visited.mark(entry.nodeid);
        if (filter && !filter->check(entry.nodeid)) {
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
        for (uint32_t neighbor_nodeid : _graph.get_link_array(cand.node_ref, level)) {
            if (neighbor_nodeid >= nodeid_limit) {
                continue;
            }
            auto neighbor_ref = _graph.acquire_node_ref(neighbor_nodeid);
            if ((! neighbor_ref.valid())
                || ! visited.try_mark(neighbor_nodeid))
            {
                continue;
            }
            double dist_to_input = calc_distance(input, neighbor_nodeid);
            if (dist_to_input < limit_dist) {
                candidates.emplace(neighbor_nodeid, neighbor_ref, dist_to_input);
                if ((!filter) || filter->check(neighbor_nodeid)) {
                    best_neighbors.emplace(neighbor_nodeid, neighbor_ref, dist_to_input);
                    if (best_neighbors.size() > neighbors_to_find) {
                        best_neighbors.pop();
                        limit_dist = best_neighbors.top().distance;
                    }
                }
            }
        }
    }
}

void
HnswIndex::search_layer(const TypedCells& input, uint32_t neighbors_to_find,
                        FurthestPriQ& best_neighbors, uint32_t level, const GlobalFilter *filter) const
{
    uint32_t nodeid_limit = _graph.node_refs_size.load(std::memory_order_acquire);
    if (filter) {
        nodeid_limit = std::min(filter->size(), nodeid_limit);
    }
    uint32_t estimated_visited_nodes = estimate_visited_nodes(level, nodeid_limit, neighbors_to_find, filter);
    if (estimated_visited_nodes >= nodeid_limit / 128) {
        search_layer_helper<BitVectorVisitedTracker>(input, neighbors_to_find, best_neighbors, level, filter, nodeid_limit, estimated_visited_nodes);
    } else {
        search_layer_helper<HashSetVisitedTracker>(input, neighbors_to_find, best_neighbors, level, filter, nodeid_limit, estimated_visited_nodes);
    }
}

HnswIndex::HnswIndex(const DocVectorAccess& vectors, DistanceFunction::UP distance_func,
                     RandomLevelGenerator::UP level_generator, const HnswIndexConfig& cfg)
    : _graph(),
      _vectors(vectors),
      _distance_func(std::move(distance_func)),
      _level_generator(std::move(level_generator)),
      _id_mapping(),
      _cfg(cfg),
      _compaction_spec()
{
    assert(_distance_func);
}

HnswIndex::~HnswIndex() = default;

void
HnswIndex::add_document(uint32_t docid)
{
    vespalib::GenerationHandler::Guard no_guard_needed;
    PreparedAddDoc op(docid, std::move(no_guard_needed));
    auto input_vectors = get_vector_by_docid(docid);
    auto subspaces = input_vectors.subspaces();
    op.nodes.reserve(subspaces);
    auto nodeids = _id_mapping.allocate_ids(docid, subspaces);
    assert(nodeids.size() == subspaces);
    for (uint32_t subspace = 0; subspace < subspaces; ++subspace) {
        auto entry = _graph.get_entry_node();
        internal_prepare_add_node(op, input_vectors.cells(subspace), entry);
        internal_complete_add_node(nodeids[subspace], docid, subspace, op.nodes.back());
    }
}

HnswIndex::PreparedAddDoc
HnswIndex::internal_prepare_add(uint32_t docid, VectorBundle input_vectors, vespalib::GenerationHandler::Guard read_guard) const
{
    assert(input_vectors.subspaces() == 1);
    PreparedAddDoc op(docid, std::move(read_guard));
    auto entry = _graph.get_entry_node();
    auto subspaces = input_vectors.subspaces();
    op.nodes.reserve(subspaces);
    for (uint32_t subspace = 0; subspace < subspaces; ++subspace) {
        internal_prepare_add_node(op, input_vectors.cells(subspace), entry);
    }
    return op;
}

void
HnswIndex::internal_prepare_add_node(HnswIndex::PreparedAddDoc& op, TypedCells input_vector, const HnswGraph::EntryNode& entry) const
{
    // TODO: Add capping on num_levels
    int node_max_level = _level_generator->max_level();
    std::vector<PreparedAddNode::Links> connections(node_max_level + 1);
    if (entry.nodeid == 0) {
        // graph has no entry point
        op.nodes.emplace_back(std::move(connections));
        return;
    }
    int search_level = entry.level;
    double entry_dist = calc_distance(input_vector, entry.nodeid);
    // TODO: check if entry nodeid/node_ref is still valid here
    HnswCandidate entry_point(entry.nodeid, entry.node_ref, entry_dist);
    while (search_level > node_max_level) {
        entry_point = find_nearest_in_layer(input_vector, entry_point, search_level);
        --search_level;
    }

    FurthestPriQ best_neighbors;
    best_neighbors.push(entry_point);
    search_level = std::min(node_max_level, search_level);
    // Find neighbors of the added document in each level it should exist in.
    while (search_level >= 0) {
        search_layer(input_vector, _cfg.neighbors_to_explore_at_construction(), best_neighbors, search_level);
        auto neighbors = select_neighbors(best_neighbors.peek(), _cfg.max_links_on_inserts());
        auto& links = connections[search_level];
        links.reserve(neighbors.used.size());
        for (const auto & neighbor : neighbors.used) {
            auto neighbor_levels = _graph.get_level_array(neighbor.node_ref);
            if (size_t(search_level) < neighbor_levels.size()) {
                links.emplace_back(neighbor.nodeid, neighbor.node_ref);
            } else {
                LOG(warning, "in prepare_add(%u), selected neighbor %u is missing level %d (has %zu levels)",
                    op.docid, neighbor.nodeid, search_level, neighbor_levels.size());
            }
        }
        --search_level;
    }
    op.nodes.emplace_back(std::move(connections));
}

HnswIndex::LinkArray 
HnswIndex::filter_valid_nodeids(uint32_t level, const PreparedAddNode::Links &neighbors, uint32_t self_nodeid)
{
    LinkArray valid;
    valid.reserve(neighbors.size());
    for (const auto & neighbor : neighbors) {
        uint32_t nodeid = neighbor.first;
        HnswGraph::NodeRef node_ref = neighbor.second;
        if (_graph.still_valid(nodeid, node_ref)) {
            assert(nodeid != self_nodeid);
            auto levels = _graph.get_level_array(node_ref);
            if (level < levels.size()) {
                valid.push_back(nodeid);
            }
        }
    }
    return valid;
}

void
HnswIndex::internal_complete_add(uint32_t docid, PreparedAddDoc &op)
{
    assert(op.nodes.size() == 1);
    auto nodeids = _id_mapping.allocate_ids(docid, op.nodes.size());
    assert(nodeids.size() == op.nodes.size());
    uint32_t subspace = 0;
    for (auto nodeid : nodeids) {
        internal_complete_add_node(nodeid, docid, subspace, op.nodes[subspace]);
        ++subspace;
    }
}

void
HnswIndex::internal_complete_add_node(uint32_t nodeid, uint32_t docid, uint32_t subspace, PreparedAddNode &prepared_node)
{
    int32_t num_levels = prepared_node.connections.size();
    auto node_ref = _graph.make_node(nodeid, docid, subspace, num_levels);
    for (int level = 0; level < num_levels; ++level) {
        auto neighbors = filter_valid_nodeids(level, prepared_node.connections[level], nodeid);
        connect_new_node(nodeid, neighbors, level);
    }
    if (num_levels - 1 > get_entry_level()) {
        _graph.set_entry_node({nodeid, node_ref, num_levels - 1});
    }
}

std::unique_ptr<PrepareResult>
HnswIndex::prepare_add_document(uint32_t docid, 
                                VectorBundle vectors,
                                vespalib::GenerationHandler::Guard read_guard) const
{
    uint32_t max_nodes = _graph.node_refs_size.load(std::memory_order_acquire);
    if (max_nodes < _cfg.min_size_before_two_phase()) {
        // the first documents added will do all work in write thread
        // to ensure they are linked together:
        return std::make_unique<PreparedFirstAddDoc>();
    }
    PreparedAddDoc op = internal_prepare_add(docid, vectors, std::move(read_guard));
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
HnswIndex::remove_node(uint32_t nodeid)
{
    bool need_new_entrypoint = (nodeid == get_entry_nodeid());
    LevelArrayRef node_levels = _graph.get_level_array(nodeid);
    for (int level = node_levels.size(); level-- > 0; ) {
        LinkArrayRef my_links = _graph.get_link_array(nodeid, level);
        for (uint32_t neighbor_id : my_links) {
            if (need_new_entrypoint) {
                auto entry_node_ref = _graph.get_node_ref(neighbor_id);
                _graph.set_entry_node({neighbor_id, entry_node_ref, level});
                need_new_entrypoint = false;
            }
            remove_link_to(neighbor_id, nodeid, level);
        }
        mutual_reconnect(my_links, level);
    }
    if (need_new_entrypoint) {
        HnswGraph::EntryNode entry;
        _graph.set_entry_node(entry);
    }
    _graph.remove_node(nodeid);
}

void
HnswIndex::remove_document(uint32_t docid)
{
    auto nodeids = _id_mapping.get_ids(docid);
    assert(nodeids.size() == 1u);
    for (auto nodeid : nodeids) {
        remove_node(nodeid);
    }
    _id_mapping.free_ids(docid);
}

void
HnswIndex::assign_generation(generation_t current_gen)
{
    // Note: RcuVector transfers hold lists as part of reallocation based on current generation.
    //       We need to set the next generation here, as it is incremented on a higher level right after this call.
    _graph.node_refs.setGeneration(current_gen + 1);
    _graph.nodes.assign_generation(current_gen);
    _graph.links.assign_generation(current_gen);
}

void
HnswIndex::reclaim_memory(generation_t oldest_used_gen)
{
    _graph.node_refs.reclaim_memory(oldest_used_gen);
    _graph.nodes.reclaim_memory(oldest_used_gen);
    _graph.links.reclaim_memory(oldest_used_gen);
}

void
HnswIndex::compact_level_arrays(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy)
{
    auto compacting_buffers = _graph.nodes.start_compact_worst_buffers(compaction_spec, compaction_strategy);
    uint32_t nodeid_limit = _graph.node_refs.size();
    auto filter = compacting_buffers->make_entry_ref_filter();
    vespalib::ArrayRef<NodeType> refs(&_graph.node_refs[0], nodeid_limit);
    for (auto& ref : refs) {
        auto node_ref = ref.ref().load_relaxed();
        if (node_ref.valid() && filter.has(node_ref)) {
            EntryRef new_node_ref = _graph.nodes.move_on_compact(node_ref);
            ref.ref().store_release(new_node_ref);
        }
    }
    compacting_buffers->finish();
}

void
HnswIndex::compact_link_arrays(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy)
{
    auto context = _graph.links.compactWorst(compaction_spec, compaction_strategy);
    uint32_t nodeid_limit = _graph.node_refs.size();
    for (uint32_t nodeid = 1; nodeid < nodeid_limit; ++nodeid) {
        EntryRef level_ref = _graph.get_node_ref(nodeid);
        if (level_ref.valid()) {
            vespalib::ArrayRef<AtomicEntryRef> refs(_graph.nodes.get_writable(level_ref));
            context->compact(refs);
        }
    }
}

bool
HnswIndex::consider_compact_level_arrays(const CompactionStrategy& compaction_strategy)
{
    if (!_graph.nodes.has_held_buffers() && _compaction_spec.level_arrays().compact()) {
        compact_level_arrays(_compaction_spec.level_arrays(), compaction_strategy);
        return true;
    }
    return false;
}

bool
HnswIndex::consider_compact_link_arrays(const CompactionStrategy& compaction_strategy)
{
    if (!_graph.links.has_held_buffers() && _compaction_spec.link_arrays().compact()) {
        compact_link_arrays(_compaction_spec.link_arrays(), compaction_strategy);
        return true;
    }
    return false;
}

bool
HnswIndex::consider_compact(const CompactionStrategy& compaction_strategy)
{
    bool result = false;
    if (consider_compact_level_arrays(compaction_strategy)) {
        result = true;
    }
    if (consider_compact_link_arrays(compaction_strategy)) {
        result = true;
    }
    return result;
}

vespalib::MemoryUsage
HnswIndex::update_stat(const CompactionStrategy& compaction_strategy)
{
    vespalib::MemoryUsage result;
    result.merge(_graph.node_refs.getMemoryUsage());
    auto level_arrays_memory_usage = _graph.nodes.getMemoryUsage();
    auto level_arrays_address_space_usage = _graph.nodes.addressSpaceUsage();
    result.merge(level_arrays_memory_usage);
    auto link_arrays_memory_usage = _graph.links.getMemoryUsage();
    auto link_arrays_address_space_usage = _graph.links.addressSpaceUsage();
    _compaction_spec = HnswIndexCompactionSpec(compaction_strategy.should_compact(level_arrays_memory_usage, level_arrays_address_space_usage),
                                               compaction_strategy.should_compact(link_arrays_memory_usage, link_arrays_address_space_usage));
    result.merge(link_arrays_memory_usage);
    return result;
}

vespalib::MemoryUsage
HnswIndex::memory_usage() const
{
    vespalib::MemoryUsage result;
    result.merge(_graph.node_refs.getMemoryUsage());
    result.merge(_graph.nodes.getMemoryUsage());
    result.merge(_graph.links.getMemoryUsage());
    return result;
}

void
HnswIndex::populate_address_space_usage(search::AddressSpaceUsage& usage) const
{
    usage.set(AddressSpaceComponents::hnsw_node_store, _graph.nodes.addressSpaceUsage());
    usage.set(AddressSpaceComponents::hnsw_link_store, _graph.links.addressSpaceUsage());
}

void
HnswIndex::get_state(const vespalib::slime::Inserter& inserter) const
{
    auto& object = inserter.insertObject();
    auto& memUsageObj = object.setObject("memory_usage");
    StateExplorerUtils::memory_usage_to_slime(memory_usage(), memUsageObj.setObject("all"));
    StateExplorerUtils::memory_usage_to_slime(_graph.node_refs.getMemoryUsage(), memUsageObj.setObject("node_refs"));
    StateExplorerUtils::memory_usage_to_slime(_graph.nodes.getMemoryUsage(), memUsageObj.setObject("nodes"));
    StateExplorerUtils::memory_usage_to_slime(_graph.links.getMemoryUsage(), memUsageObj.setObject("links"));
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
    auto count_result = count_reachable_nodes();
    uint32_t unreachable = valid_nodes - count_result.first;
    if (count_result.second) {
        object.setLong("unreachable_nodes", unreachable);
    } else {
        object.setLong("unreachable_nodes_incomplete_count", unreachable);
    }
    auto entry_node = _graph.get_entry_node();
    object.setLong("entry_nodeid", entry_node.nodeid);
    object.setLong("entry_level", entry_node.level);
    auto& cfgObj = object.setObject("cfg");
    cfgObj.setLong("max_links_at_level_0", _cfg.max_links_at_level_0());
    cfgObj.setLong("max_links_on_inserts", _cfg.max_links_on_inserts());
    cfgObj.setLong("neighbors_to_explore_at_construction",
                   _cfg.neighbors_to_explore_at_construction());
}

void
HnswIndex::shrink_lid_space(uint32_t doc_id_limit)
{
    assert(doc_id_limit >= 1u);
    assert(doc_id_limit >= _graph.node_refs_size.load(std::memory_order_relaxed));
    uint32_t old_doc_id_limit = _graph.node_refs.size();
    if (doc_id_limit >= old_doc_id_limit) {
        return;
    }
    _graph.node_refs.shrink(doc_id_limit);
}

std::unique_ptr<NearestNeighborIndexSaver>
HnswIndex::make_saver() const
{
    return std::make_unique<HnswIndexSaver>(_graph);
}

std::unique_ptr<NearestNeighborIndexLoader>
HnswIndex::make_loader(FastOS_FileInterface& file)
{
    assert(get_entry_nodeid() == 0); // cannot load after index has data
    using ReaderType = FileReader<uint32_t>;
    using LoaderType = HnswIndexLoader<ReaderType>;
    return std::make_unique<LoaderType>(_graph, std::make_unique<ReaderType>(&file));
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
                          const GlobalFilter *filter, uint32_t explore_k,
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
        result.emplace_back(get_docid(hit.nodeid), hit.distance);
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
                                  const GlobalFilter &filter, uint32_t explore_k,
                                  double distance_threshold) const
{
    return top_k_by_docid(k, vector, &filter, explore_k, distance_threshold);
}

FurthestPriQ
HnswIndex::top_k_candidates(const TypedCells &vector, uint32_t k, const GlobalFilter *filter) const
{
    FurthestPriQ best_neighbors;
    auto entry = _graph.get_entry_node();
    if (entry.nodeid == 0) {
        // graph has no entry point
        return best_neighbors;
    }
    int search_level = entry.level;
    double entry_dist = calc_distance(vector, entry.nodeid);
    // TODO: check if entry docid/node_ref is still valid here
    HnswCandidate entry_point(entry.nodeid, entry.node_ref, entry_dist);
    while (search_level > 0) {
        entry_point = find_nearest_in_layer(vector, entry_point, search_level);
        --search_level;
    }
    best_neighbors.push(entry_point);
    search_layer(vector, k, best_neighbors, 0, filter);
    return best_neighbors;
}

HnswTestNode
HnswIndex::get_node(uint32_t nodeid) const
{
    auto node_ref = _graph.acquire_node_ref(nodeid);
    if (!node_ref.valid()) {
        return HnswTestNode();
    }
    auto levels = _graph.nodes.get(node_ref);
    HnswTestNode::LevelArray result;
    for (const auto& links_ref : levels) {
        auto links = _graph.links.get(links_ref.load_acquire());
        HnswTestNode::LinkArray result_links(links.begin(), links.end());
        std::sort(result_links.begin(), result_links.end());
        result.push_back(result_links);
    }
    return HnswTestNode(result);
}

void
HnswIndex::set_node(uint32_t nodeid, const HnswTestNode &node)
{
    size_t num_levels = node.size();
    assert(num_levels > 0);
    auto node_ref = _graph.make_node(nodeid, nodeid, 0, num_levels);
    for (size_t level = 0; level < num_levels; ++level) {
        connect_new_node(nodeid, node.level(level), level);
    }
    int max_level = num_levels - 1;
    if (get_entry_level() < max_level) {
        _graph.set_entry_node({nodeid, node_ref, max_level});
    }
}

bool
HnswIndex::check_link_symmetry() const
{
    bool all_sym = true;
    size_t nodeid_limit = _graph.size();
    for (size_t nodeid = 0; nodeid < nodeid_limit; ++nodeid) {
        auto node_ref = _graph.acquire_node_ref(nodeid);
        if (node_ref.valid()) {
            auto levels = _graph.nodes.get(node_ref);
            uint32_t level = 0;
            for (const auto& links_ref : levels) {
                auto links = _graph.links.get(links_ref.load_acquire());
                for (auto neighbor_nodeid : links) {
                    auto neighbor_links = _graph.acquire_link_array(neighbor_nodeid, level);
                    if (! has_link_to(neighbor_links, nodeid)) {
                        all_sym = false;
                        LOG(warning, "check_link_symmetry: nodeid %zu links to %u on level %u, but no backlink",
                            nodeid, neighbor_nodeid, level);
                    }
                }
                ++level;
            }
        }
    }
    return all_sym;
}

std::pair<uint32_t, bool>
HnswIndex::count_reachable_nodes() const
{
    auto entry = _graph.get_entry_node();
    int search_level = entry.level;
    if (search_level < 0) {
        return {0, true};
    }
    std::vector<bool> visited(_graph.size());
    LinkArray found_links;
    if (entry.nodeid < visited.size()) {
        found_links.push_back(entry.nodeid);
        visited[entry.nodeid] = true;
    }
    vespalib::steady_time doom = vespalib::steady_clock::now() + MAX_COUNT_DURATION;
    while (search_level >= 0) {
        for (uint32_t idx = 0; idx < found_links.size(); ++idx) {
            if (vespalib::steady_clock::now() > doom) {
                return {found_links.size(), false};
            }
            uint32_t nodeid = found_links[idx];
            if (nodeid < visited.size()) {
                auto neighbors = _graph.acquire_link_array(nodeid, search_level);
                for (uint32_t neighbor : neighbors) {
                    if (neighbor >= visited.size() || visited[neighbor]) {
                        continue;
                    }
                    visited[neighbor] = true;
                    found_links.push_back(neighbor);
                }
            }
        }
        --search_level;
    }
    return {found_links.size(), true};
}

} // namespace
