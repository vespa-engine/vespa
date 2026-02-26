// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_index.h"
#include "bitvector_visited_tracker.h"
#include "hash_set_visited_tracker.h"
#include "hnsw_index_explorer.h"
#include "hnsw_index_loader.hpp"
#include "hnsw_index_saver.h"
#include "mips_distance_transform.h"
#include "random_level_generator.h"
#include "vector_bundle.h"
#include <vespa/searchlib/attribute/address_space_components.h>
#include <vespa/searchlib/attribute/address_space_usage.h>
#include <vespa/searchlib/util/fileutil.h>
#include <vespa/vespalib/datastore/array_store.hpp>
#include <vespa/vespalib/util/doom.h>
#include <vespa/vespalib/util/memory_allocator.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/log/log.h>

LOG_SETUP(".searchlib.tensor.hnsw_index");

namespace search::tensor {

using search::AddressSpaceComponents;
using search::queryeval::GlobalFilter;
using vespalib::datastore::ArrayStoreConfig;
using vespalib::datastore::CompactionStrategy;
using vespalib::datastore::EntryRef;
using vespalib::GenericHeader;

namespace {

constexpr size_t min_num_arrays_for_new_buffer = 512_Ki;
constexpr float alloc_grow_factor = 0.3;
// TODO: Adjust these numbers to what we accept as max in config.
constexpr size_t max_level_array_size = 16;
constexpr size_t max_link_array_size = 193;
constexpr vespalib::duration MAX_COUNT_DURATION(1000ms);

const std::string hnsw_max_squared_norm = "hnsw.max_squared_norm";

void save_mips_max_distance(GenericHeader& header, DistanceFunctionFactory& dff) {
    auto* mips_dff = dynamic_cast<MipsDistanceFunctionFactoryBase*>(&dff);
    if (mips_dff != nullptr) {
        auto& norm_store = mips_dff->get_max_squared_norm_store();
        header.putTag(GenericHeader::Tag(hnsw_max_squared_norm, norm_store.get_max()));
    }
}

void load_mips_max_distance(const GenericHeader& header, DistanceFunctionFactory& dff) {
    auto* mips_dff = dynamic_cast<MipsDistanceFunctionFactoryBase*>(&dff);
    if (mips_dff != nullptr) {
        auto& norm_store = mips_dff->get_max_squared_norm_store();
        if (header.hasTag(hnsw_max_squared_norm)) {
            auto& tag = header.getTag(hnsw_max_squared_norm);
            if (tag.getType() == GenericHeader::Tag::Type::TYPE_FLOAT) {
                (void) norm_store.get_max(tag.asFloat());
            }
        }
    }
}

bool has_link_to(std::span<const uint32_t> links, uint32_t id) {
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
bool operator< (const PairDist &a, const PairDist &b) noexcept {
    return (a.distance < b.distance);
}

}

namespace internal {

PreparedAddNode::PreparedAddNode() noexcept
    : connections()
{ }
PreparedAddNode::PreparedAddNode(std::vector<Links>&& connections_in) noexcept
    : connections(std::move(connections_in))
{ }
PreparedAddNode::~PreparedAddNode() = default;
PreparedAddNode::PreparedAddNode(PreparedAddNode&& other) noexcept = default;

PreparedAddDoc::PreparedAddDoc(uint32_t docid_in, ReadGuard read_guard_in) noexcept
    : docid(docid_in),
      read_guard(std::move(read_guard_in)),
      nodes()
{}
PreparedAddDoc::~PreparedAddDoc() = default;
PreparedAddDoc::PreparedAddDoc(PreparedAddDoc&& other) noexcept = default;
}

SelectResult::SelectResult() noexcept = default;
SelectResult::~SelectResult() = default;

template <HnswIndexType type>
ArrayStoreConfig
HnswIndex<type>::make_default_level_array_store_config()
{
    return LevelArrayStore::optimizedConfigForHugePage(max_level_array_size,
                                                       vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                       vespalib::alloc::MemoryAllocator::PAGE_SIZE,
                                                       ArrayStoreConfig::default_max_buffer_size,
                                                       min_num_arrays_for_new_buffer,
                                                       alloc_grow_factor).enable_free_lists(true);
}

template <HnswIndexType type>
ArrayStoreConfig
HnswIndex<type>::make_default_link_array_store_config()
{
    return LinkArrayStore::optimizedConfigForHugePage(max_link_array_size,
                                                      vespalib::alloc::MemoryAllocator::HUGEPAGE_SIZE,
                                                      vespalib::alloc::MemoryAllocator::PAGE_SIZE,
                                                      ArrayStoreConfig::default_max_buffer_size,
                                                      min_num_arrays_for_new_buffer,
                                                      alloc_grow_factor).enable_free_lists(true);
}

template <HnswIndexType type>
uint32_t
HnswIndex<type>::max_links_for_level(uint32_t level) const
{
    return (level == 0) ? _cfg.max_links_at_level_0() : _cfg.max_links_on_inserts();
}

template <HnswIndexType type>
bool
HnswIndex<type>::have_closer_distance(HnswTraversalCandidate candidate, const HnswTraversalCandidateVector& result) const
{
    auto candidate_vector = get_vector(candidate.nodeid);
    if (candidate_vector.non_existing_attribute_value()) {
        /*
         * We are in a read thread and the write thread has removed the
         * tensor for the candidate. Return true to prevent the candidate
         * from being considered.
         */
        return true;
    }
    auto df = _distance_ff->for_insertion_vector(candidate_vector);
    for (const auto & neighbor : result) {
        double dist = calc_distance(*df, neighbor.nodeid);
        if (dist < candidate.distance) {
            return true;
        }
    }
    return false;
}

template <HnswIndexType type>
template <typename HnswCandidateVectorT>
SelectResult
HnswIndex<type>::select_neighbors_simple(const HnswCandidateVectorT& neighbors, uint32_t max_links) const
{
    HnswCandidateVectorT sorted(neighbors);
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

template <HnswIndexType type>
template <typename HnswCandidateVectorT>
SelectResult
HnswIndex<type>::select_neighbors_heuristic(const HnswCandidateVectorT& neighbors, uint32_t max_links) const
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

template <HnswIndexType type>
template <typename HnswCandidateVectorT>
SelectResult
HnswIndex<type>::select_neighbors(const HnswCandidateVectorT& neighbors, uint32_t max_links) const
{
    if (_cfg.heuristic_select_neighbors()) {
        return select_neighbors_heuristic(neighbors, max_links);
    } else {
        return select_neighbors_simple(neighbors, max_links);
    }
}

template <HnswIndexType type>
void
HnswIndex<type>::shrink_if_needed(uint32_t nodeid, uint32_t level)
{
    auto old_links = _graph.get_link_array(nodeid, level);
    uint32_t max_links = max_links_for_level(level);
    if (old_links.size() > max_links) {
        HnswTraversalCandidateVector neighbors;
        neighbors.reserve(old_links.size());
        auto df = _distance_ff->for_insertion_vector(get_vector(nodeid));
        for (uint32_t neighbor_nodeid : old_links) {
            double dist = calc_distance(*df, neighbor_nodeid);
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

template <HnswIndexType type>
void
HnswIndex<type>::connect_new_node(uint32_t nodeid, const LinkArrayRef &neighbors, uint32_t level)
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

template <HnswIndexType type>
void
HnswIndex<type>::remove_link_to(uint32_t remove_from, uint32_t remove_id, uint32_t level)
{
    LinkArray new_links;
    auto old_links = _graph.get_link_array(remove_from, level);
    new_links.reserve(old_links.size());
    for (uint32_t id : old_links) {
        if (id != remove_id) new_links.push_back(id);
    }
    _graph.set_link_array(remove_from, level, new_links);
}

namespace {

double
calc_distance_helper(const BoundDistanceFunction &df, vespalib::eval::TypedCells rhs)
{
    if (rhs.non_existing_attribute_value()) [[unlikely]] {
        /*
         * We are in a read thread and the write thread has removed the
         * tensor.
         */
        return std::numeric_limits<double>::max();
    }
    return df.calc(rhs);
}

void prefetch_docid(const DocVectorAccess& vectors, uint32_t docid)
{
    vectors.prefetch_docid(docid);
}

void prefetch_vector(const DocVectorAccess& vectors, uint32_t docid)
{
    vectors.prefetch_vector(docid);
}

}

template <HnswIndexType type>
double
HnswIndex<type>::calc_distance(const BoundDistanceFunction &df, uint32_t rhs_nodeid) const
{
    auto rhs = get_vector(rhs_nodeid);
    return calc_distance_helper(df, rhs);
}

template <HnswIndexType type>
double
HnswIndex<type>::calc_distance(const BoundDistanceFunction &df, uint32_t rhs_docid, uint32_t rhs_subspace) const
{
    auto rhs = get_vector(rhs_docid, rhs_subspace);
    return calc_distance_helper(df, rhs);
}

template <HnswIndexType type>
uint32_t
HnswIndex<type>::estimate_visited_nodes(uint32_t level, uint32_t nodeid_limit, uint32_t neighbors_to_find, const GlobalFilter* filter) const
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

template <HnswIndexType type>
HnswCandidate
HnswIndex<type>::find_nearest_in_layer(Stats &stats, const BoundDistanceFunction &df, const HnswCandidate& entry_point, uint32_t level) const
{
    HnswCandidate nearest = entry_point;
    bool keep_searching = true;
    while (keep_searching) {
        keep_searching = false;
        for (uint32_t neighbor_nodeid : _graph.get_link_array(nearest.levels_ref, level)) {
            stats.count_visited_node();
            auto& neighbor_node = _graph.acquire_node(neighbor_nodeid);
            auto neighbor_ref = neighbor_node.levels_ref().load_acquire();
            uint32_t neighbor_docid = acquire_docid(neighbor_node, neighbor_nodeid);
            uint32_t neighbor_subspace = neighbor_node.acquire_subspace();
            double dist = calc_distance(df, neighbor_docid, neighbor_subspace);
            stats.count_computed_distance();
            if (_graph.still_valid(neighbor_nodeid, neighbor_ref)
                && dist < nearest.distance)
            {
                nearest = HnswCandidate(neighbor_nodeid, neighbor_docid, neighbor_ref, dist);
                keep_searching = true;
            }
        }
    }
    return nearest;
}


template <HnswIndexType type>
template <class VisitedTracker, class BestNeighbors>
void
HnswIndex<type>::search_layer_helper(Stats &stats, const BoundDistanceFunction &df, uint32_t neighbors_to_find, double exploration_slack, bool prefetch_tensors,
                                     BestNeighbors& best_neighbors, uint32_t level, const GlobalFilter *filter,
                                     uint32_t nodeid_limit, const vespalib::Doom* const doom,
                                     uint32_t estimated_visited_nodes) const
{
    NearestPriQ candidates;
    internal::GlobalFilterWrapper<type> filter_wrapper(filter);
    filter_wrapper.clamp_nodeid_limit(nodeid_limit);
    VisitedTracker visited(nodeid_limit, estimated_visited_nodes);
    if (doom != nullptr && doom->soft_doom()) {
        while (!best_neighbors.empty()) {
            best_neighbors.pop();
        }
        return;
    }
    for (const auto &entry : best_neighbors.peek()) {
        if (entry.nodeid >= nodeid_limit) {
            continue;
        }
        candidates.push(entry);
        visited.mark(entry.nodeid);
        if (!filter_wrapper.check(entry.docid)) {
            assert(best_neighbors.peek().size() == 1);
            best_neighbors.pop();
        }
    }
    double limit_dist = std::numeric_limits<double>::max();

    struct NeighborLinkMeta final {
        uint32_t neighbor_nodeid;
        GraphType::LevelsRef neighbor_ref;
        uint32_t neighbor_docid;
        uint32_t neighbor_subspace;

        constexpr NeighborLinkMeta(uint32_t neighbor_nodeid_in, GraphType::LevelsRef neighbor_ref_in,
                                   uint32_t neighbor_docid_in, uint32_t neighbor_subspace_in) noexcept
            : neighbor_nodeid(neighbor_nodeid_in),
              neighbor_ref(neighbor_ref_in),
              neighbor_docid(neighbor_docid_in),
              neighbor_subspace(neighbor_subspace_in) {}
    };
    std::vector<NeighborLinkMeta> neighbor_link_metas;
    neighbor_link_metas.reserve(max_links_for_level(level));

    while (!candidates.empty()) {
        auto cand = candidates.top();
        if (cand.distance > (1.0 + exploration_slack) * limit_dist) {
            break;
        }
        candidates.pop();

        const auto link_array = _graph.get_link_array(cand.levels_ref, level);
        neighbor_link_metas.clear();
        for (const auto neighbor_nodeid : link_array) {
            if (neighbor_nodeid >= nodeid_limit) {
                continue;
            }
            const auto& neighbor_node = _graph.acquire_node(neighbor_nodeid);

            const auto neighbor_ref = neighbor_node.levels_ref().load_acquire();
            if ((! neighbor_ref.valid())
                || ! visited.try_mark(neighbor_nodeid)) {
                continue;
            }
            stats.count_visited_node();

            const auto neighbor_docid = acquire_docid(neighbor_node, neighbor_nodeid);
            const auto neighbor_subspace = neighbor_node.acquire_subspace();

            neighbor_link_metas.emplace_back(
                neighbor_nodeid,
                neighbor_ref,
                neighbor_docid,
                neighbor_subspace
            );

            prefetch_docid(_vectors, neighbor_docid);
        }

        if (prefetch_tensors) {
            for (const auto& link : neighbor_link_metas) {
                prefetch_vector(_vectors, link.neighbor_docid);
            }
        }

        for (const auto& link : neighbor_link_metas) {
            double dist_to_input = calc_distance(df, link.neighbor_docid, link.neighbor_subspace);
            stats.count_computed_distance();
            if (dist_to_input < (1.0 + exploration_slack) * limit_dist) {
                candidates.emplace(link.neighbor_nodeid, link.neighbor_ref, dist_to_input);

                if (dist_to_input < limit_dist && filter_wrapper.check(link.neighbor_docid)) {
                    best_neighbors.emplace(link.neighbor_nodeid, link.neighbor_docid, link.neighbor_ref, dist_to_input);
                    while (best_neighbors.size() > neighbors_to_find) {
                        best_neighbors.pop();
                        limit_dist = best_neighbors.top().distance;
                    }
                }
            }
        }
        if (doom != nullptr && doom->soft_doom()) {
            break;
        }
    }
}


template <HnswIndexType type>
template <class VisitedTracker, class BestNeighbors>
void
HnswIndex<type>::search_layer_filter_first_helper(Stats &stats, const BoundDistanceFunction &df, uint32_t neighbors_to_find, double exploration_slack,
                                                  BestNeighbors& best_neighbors, double exploration, uint32_t level, const GlobalFilter *filter,
                                                  uint32_t nodeid_limit, const vespalib::Doom* const doom,
                                                  uint32_t estimated_visited_nodes) const
{
    assert(filter);
    NearestPriQ candidates;
    internal::GlobalFilterWrapper<type> filter_wrapper(filter);
    filter_wrapper.clamp_nodeid_limit(nodeid_limit);
    VisitedTracker visited(nodeid_limit, estimated_visited_nodes);
    if (doom != nullptr && doom->soft_doom()) {
        while (!best_neighbors.empty()) {
            best_neighbors.pop();
        }
        return;
    }
    for (const auto &entry : best_neighbors.peek()) {
        if (entry.nodeid >= nodeid_limit) {
            continue;
        }
        candidates.push(entry);
        visited.mark(entry.nodeid);
        if (!filter_wrapper.check(entry.docid)) {
            assert(best_neighbors.peek().size() == 1);
            best_neighbors.pop();
        }
    }
    double limit_dist = std::numeric_limits<double>::max();

    std::deque<uint32_t> neighborhood;
    while (!candidates.empty()) {
        auto cand = candidates.top();
        if (cand.distance > (1.0 + exploration_slack) * limit_dist) {
            break;
        }
        candidates.pop();

        // Instead of taking immediate neighbors, we additionally explore 2-hop neighbors (and possibly 3-hop neighbors)
        neighborhood.clear();
        exploreNeighborhood(stats, cand, neighborhood, visited, exploration, level, filter_wrapper, nodeid_limit);

        for (uint32_t neighbor_nodeid : neighborhood) {
            auto& neighbor_node = _graph.acquire_node(neighbor_nodeid);
            auto neighbor_ref = neighbor_node.levels_ref().load_acquire();
            if (! neighbor_ref.valid()) {
                continue;
            }
            uint32_t neighbor_docid = acquire_docid(neighbor_node, neighbor_nodeid);
            uint32_t neighbor_subspace = neighbor_node.acquire_subspace();
            double dist_to_input = calc_distance(df, neighbor_docid, neighbor_subspace);
            stats.count_computed_distance();
            if (dist_to_input < (1.0 + exploration_slack) * limit_dist) {
                candidates.emplace(neighbor_nodeid, neighbor_ref, dist_to_input);

                if (dist_to_input < limit_dist) {
                    // exploreNeighborhood only returns nodes that pass the filter, no need to check that here
                    best_neighbors.emplace(neighbor_nodeid, neighbor_docid, neighbor_ref, dist_to_input);
                    while (best_neighbors.size() > neighbors_to_find) {
                        best_neighbors.pop();
                        limit_dist = best_neighbors.top().distance;
                    }
                }
            }
        }
        if (doom != nullptr && doom->soft_doom()) {
            break;
        }
    }
}

template <HnswIndexType type>
template <class VisitedTracker>
void
HnswIndex<type>::exploreNeighborhood(Stats &stats, HnswTraversalCandidate &cand, std::deque<uint32_t> &found, VisitedTracker &visited, double exploration,
                                     uint32_t level, const internal::GlobalFilterWrapper<type>& filter_wrapper, uint32_t nodeid_limit) const {
    assert(found.empty());

    std::deque<uint32_t> todo;
    todo.push_back(cand.nodeid);

    uint32_t max_neighbors_to_find = max_links_for_level(level);

    // Explore (1-hop) neighbors
    exploreNeighborhoodByOneHop(stats, todo, found, visited, level, filter_wrapper, nodeid_limit, max_neighbors_to_find);

    // Explore 2-hop neighbors
    exploreNeighborhoodByOneHop(stats, todo, found, visited, level, filter_wrapper, nodeid_limit, max_neighbors_to_find);

    // Explore 3-hop neighbors, but only if the 2-hop neighborhood is small, i.e., we are in a rather sparse part of the graph.
    if (static_cast<double>(todo.size()) < exploration * (max_neighbors_to_find * max_neighbors_to_find)) {
        exploreNeighborhoodByOneHop(stats, todo, found, visited, level, filter_wrapper, nodeid_limit, max_neighbors_to_find);
    }
}

template <HnswIndexType type>
template <class VisitedTracker>
void
HnswIndex<type>::exploreNeighborhoodByOneHop(Stats &stats, std::deque<uint32_t> &todo, std::deque<uint32_t> &found, VisitedTracker &visited, uint32_t level,
                                             const internal::GlobalFilterWrapper<type>& filter_wrapper, uint32_t nodeid_limit,
                                             uint32_t max_neighbors_to_find) const {
    // We do not explore the candidates that we newly add to the deque
    uint32_t nodesToExplore = todo.size();
    for (uint32_t nodesExplored = 0; nodesExplored < nodesToExplore && found.size() < max_neighbors_to_find; ++nodesExplored) {
        uint32_t nodeid = todo.front();
        todo.pop_front();
        auto& node = _graph.acquire_node(nodeid);
        auto ref = node.levels_ref().load_acquire();

        for (uint32_t neighbor_nodeid : _graph.get_link_array(ref, level)) {
            if (neighbor_nodeid >= nodeid_limit) {
                continue;
            }
            // Just explore everything in the next hop, even if the current node passes the filter or was marked as visited
            todo.push_back(neighbor_nodeid);

            // Skip if the current node was marked as visited (-> We already checked if it passes the filter)
            auto& neighbor_node = _graph.acquire_node(neighbor_nodeid);
            auto neighbor_ref = neighbor_node.levels_ref().load_acquire();
            if (!neighbor_ref.valid() || !visited.try_mark(neighbor_nodeid)) {
                continue;
            }
            stats.count_visited_node();

            uint32_t neighbor_docid = acquire_docid(neighbor_node, neighbor_nodeid);
            if (filter_wrapper.check(neighbor_docid)) {
                found.push_back(neighbor_nodeid);

                // Abort if we already found enough neighbors
                if (found.size() >= max_neighbors_to_find) {
                    return;
                }
            }
        }
    }
}


template <HnswIndexType type>
template <class BestNeighbors>
void
HnswIndex<type>::search_layer(Stats &stats, const BoundDistanceFunction &df, uint32_t neighbors_to_find, double exploration_slack, bool prefetch_tensors, BestNeighbors& best_neighbors,
                              uint32_t level, const vespalib::Doom* const doom, const GlobalFilter *filter) const
{
    uint32_t nodeid_limit = _graph.nodes_size.load(std::memory_order_acquire);
    uint32_t estimated_visited_nodes = estimate_visited_nodes(level, nodeid_limit, neighbors_to_find, filter);
    if (estimated_visited_nodes >= nodeid_limit / 128) {
        search_layer_helper<BitVectorVisitedTracker>(stats, df, neighbors_to_find, exploration_slack, prefetch_tensors, best_neighbors, level, filter, nodeid_limit, doom, estimated_visited_nodes);
    } else {
        search_layer_helper<HashSetVisitedTracker>(stats, df, neighbors_to_find, exploration_slack, prefetch_tensors, best_neighbors, level, filter, nodeid_limit, doom, estimated_visited_nodes);
    }
}

template <HnswIndexType type>
template <class BestNeighbors>
void
HnswIndex<type>::search_layer_filter_first(Stats &stats, const BoundDistanceFunction &df, uint32_t neighbors_to_find, double exploration_slack, BestNeighbors& best_neighbors, double exploration,
                                           uint32_t level, const vespalib::Doom* const doom, const GlobalFilter *filter) const
{
    uint32_t nodeid_limit = _graph.nodes_size.load(std::memory_order_acquire);
    uint32_t estimated_visited_nodes = estimate_visited_nodes(level, nodeid_limit, neighbors_to_find, filter);
    if (estimated_visited_nodes >= nodeid_limit / 128) {
        search_layer_filter_first_helper<BitVectorVisitedTracker>(stats, df, neighbors_to_find, exploration_slack, best_neighbors, exploration, level, filter, nodeid_limit, doom, estimated_visited_nodes);
    } else {
        search_layer_filter_first_helper<HashSetVisitedTracker>(stats, df, neighbors_to_find, exploration_slack, best_neighbors, exploration, level, filter, nodeid_limit, doom, estimated_visited_nodes);
    }
}

template <HnswIndexType type>
HnswIndex<type>::HnswIndex(const DocVectorAccess& vectors, DistanceFunctionFactory::UP distance_ff,
                           RandomLevelGenerator::UP level_generator, const HnswIndexConfig& cfg)
    : _graph(),
      _vectors(vectors),
      _distance_ff(std::move(distance_ff)),
      _level_generator(std::move(level_generator)),
      _id_mapping(),
      _cfg(cfg)
{
    assert(_distance_ff);
}

template <HnswIndexType type>
HnswIndex<type>::~HnswIndex() = default;

using internal::PreparedAddNode;
using internal::PreparedAddDoc;
using internal::PreparedFirstAddDoc;

template <HnswIndexType type>
void
HnswIndex<type>::add_document(uint32_t docid)
{
    vespalib::GenerationHandler::Guard no_guard_needed;
    PreparedAddDoc op(docid, std::move(no_guard_needed));
    auto input_vectors = get_vectors(docid);
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

template <HnswIndexType type>
PreparedAddDoc
HnswIndex<type>::internal_prepare_add(uint32_t docid, VectorBundle input_vectors, vespalib::GenerationHandler::Guard read_guard) const
{
    PreparedAddDoc op(docid, std::move(read_guard));
    auto entry = _graph.get_entry_node();
    auto subspaces = input_vectors.subspaces();
    op.nodes.reserve(subspaces);
    for (uint32_t subspace = 0; subspace < subspaces; ++subspace) {
        internal_prepare_add_node(op, input_vectors.cells(subspace), entry);
    }
    return op;
}

template <HnswIndexType type>
void
HnswIndex<type>::internal_prepare_add_node(PreparedAddDoc& op, TypedCells input_vector, const typename GraphType::EntryNode& entry) const
{
    Stats stats; // Not used for now. Wire this in to all places where distances are computed if to be used.
    int node_max_level = std::min(_level_generator->max_level(), max_max_level);
    std::vector<PreparedAddNode::Links> connections(node_max_level + 1);
    if (entry.nodeid == 0) {
        // graph has no entry point
        op.nodes.emplace_back(std::move(connections));
        return;
    }
    int search_level = entry.level;
    auto df = _distance_ff->for_insertion_vector(input_vector);
    double entry_dist = calc_distance(*df, entry.nodeid);
    uint32_t entry_docid = get_docid(entry.nodeid);
    // TODO: check if entry nodeid/levels_ref is still valid here
    HnswCandidate entry_point(entry.nodeid, entry_docid, entry.levels_ref, entry_dist);
    while (search_level > node_max_level) {
        entry_point = find_nearest_in_layer(stats, *df, entry_point, search_level);
        --search_level;
    }

    FurthestPriQ best_neighbors;
    best_neighbors.push(entry_point);
    search_level = std::min(node_max_level, search_level);
    // Find neighbors of the added document in each level it should exist in.
    while (search_level >= 0) {
        search_layer(stats, *df, _cfg.neighbors_to_explore_at_construction(), 0.0, false, best_neighbors, search_level, nullptr);
        auto neighbors = select_neighbors(best_neighbors.peek(), _cfg.max_links_on_inserts());
        auto& links = connections[search_level];
        links.reserve(neighbors.used.size());
        for (const auto & neighbor : neighbors.used) {
            auto neighbor_levels = _graph.get_level_array(neighbor.levels_ref);
            if (size_t(search_level) < neighbor_levels.size()) {
                links.emplace_back(neighbor.nodeid, neighbor.levels_ref);
            } else {
                LOG(warning, "in prepare_add(%u), selected neighbor %u is missing level %d (has %zu levels)",
                    op.docid, neighbor.nodeid, search_level, neighbor_levels.size());
            }
        }
        --search_level;
    }
    op.nodes.emplace_back(std::move(connections));
}

template <HnswIndexType type>
LinkArray
HnswIndex<type>::filter_valid_nodeids(uint32_t level, const typename PreparedAddNode::Links &neighbors, uint32_t self_nodeid)
{
    LinkArray valid;
    valid.reserve(neighbors.size());
    for (const auto & neighbor : neighbors) {
        uint32_t nodeid = neighbor.first;
        vespalib::datastore::EntryRef levels_ref = neighbor.second;
        if (_graph.still_valid(nodeid, levels_ref)) {
            assert(nodeid != self_nodeid);
            auto levels = _graph.get_level_array(levels_ref);
            if (level < levels.size()) {
                valid.push_back(nodeid);
            }
        }
    }
    return valid;
}

template <HnswIndexType type>
void
HnswIndex<type>::internal_complete_add(uint32_t docid, PreparedAddDoc &op)
{
    auto nodeids = _id_mapping.allocate_ids(docid, op.nodes.size());
    assert(nodeids.size() == op.nodes.size());
    uint32_t subspace = 0;
    for (auto nodeid : nodeids) {
        internal_complete_add_node(nodeid, docid, subspace, op.nodes[subspace]);
        ++subspace;
    }
}

template <HnswIndexType type>
void
HnswIndex<type>::internal_complete_add_node(uint32_t nodeid, uint32_t docid, uint32_t subspace, PreparedAddNode &prepared_node)
{
    int32_t num_levels = prepared_node.connections.size();
    auto levels_ref = _graph.make_node(nodeid, docid, subspace, num_levels);
    for (int level = 0; level < num_levels; ++level) {
        auto neighbors = filter_valid_nodeids(level, prepared_node.connections[level], nodeid);
        connect_new_node(nodeid, neighbors, level);
    }
    if (num_levels - 1 > get_entry_level()) {
        _graph.set_entry_node({nodeid, levels_ref, num_levels - 1});
    }
}

template <HnswIndexType type>
std::unique_ptr<PrepareResult>
HnswIndex<type>::prepare_add_document(uint32_t docid, VectorBundle vectors, vespalib::GenerationHandler::Guard read_guard) const
{
    uint32_t active_nodes = _graph.get_active_nodes();
    if (active_nodes < _cfg.min_size_before_two_phase()) {
        // the first documents added will do all work in write thread
        // to ensure they are linked together:
        return std::make_unique<PreparedFirstAddDoc>();
    }
    PreparedAddDoc op = internal_prepare_add(docid, vectors, std::move(read_guard));
    return std::make_unique<PreparedAddDoc>(std::move(op));
}

template <HnswIndexType type>
void
HnswIndex<type>::complete_add_document(uint32_t docid, std::unique_ptr<PrepareResult> prepare_result)
{
    auto prepared = dynamic_cast<PreparedAddDoc *>(prepare_result.get());
    if (prepared && (prepared->docid == docid)) {
        internal_complete_add(docid, *prepared);
    } else {
        // we expect this for the first documents added, so no warning for them
        if (_graph.get_active_nodes() > 1.25 * _cfg.min_size_before_two_phase()) {
            LOG(warning, "complete_add_document(%u) called with invalid prepare_result %s/%u",
                docid, (prepared ? "valid ptr" : "nullptr"), (prepared ? prepared->docid : 0u));
        }
        // fallback to normal add
        add_document(docid);
    }
}

template <HnswIndexType type>
void
HnswIndex<type>::mutual_reconnect(const LinkArrayRef &cluster, uint32_t level)
{
    std::vector<PairDist> pairs;
    for (uint32_t i = 0; i + 1 < cluster.size(); ++i) {
        uint32_t n_id_1 = cluster[i];
        TypedCells n_cells_1 = get_vector(n_id_1);
        if (n_cells_1.non_existing_attribute_value()) [[unlikely]] continue;
        LinkArrayRef n_list_1 = _graph.get_link_array(n_id_1, level);
        std::unique_ptr<BoundDistanceFunction> df = _distance_ff->for_insertion_vector(n_cells_1);
        for (uint32_t j = i + 1; j < cluster.size(); ++j) {
            uint32_t n_id_2 = cluster[j];
            if ( ! has_link_to(n_list_1, n_id_2)) {
                auto n_cells_2 = get_vector(n_id_2);
                if (!n_cells_2.non_existing_attribute_value()) {
                    pairs.emplace_back(n_id_1, n_id_2, df->calc(n_cells_2));
                }
            }
        }
    }
    std::sort(pairs.begin(), pairs.end());

    // Only add at most one edge per nodes
    vespalib::hash_set<uint32_t> already_added;
    for (const PairDist & pair : pairs) {
        LinkArrayRef old_links_1 = _graph.get_link_array(pair.id_first, level);
        if (old_links_1.size() >= max_links_for_level(level)) continue;

        LinkArrayRef old_links_2 = _graph.get_link_array(pair.id_second, level);
        if (old_links_2.size() >= max_links_for_level(level)) continue;

        if ((old_links_1.empty() || old_links_2.empty())
            || (!already_added.contains(pair.id_first) && !already_added.contains(pair.id_second))) {

            already_added.insert(pair.id_first);
            already_added.insert(pair.id_second);

            add_link_to(pair.id_first, level, old_links_1, pair.id_second);
            add_link_to(pair.id_second, level, old_links_2, pair.id_first);
        }
    }
}

template <HnswIndexType type>
void
HnswIndex<type>::remove_node(uint32_t nodeid)
{
    bool need_new_entrypoint = (nodeid == get_entry_nodeid());
    LevelArrayRef node_levels = _graph.get_level_array(nodeid);
    for (int level = node_levels.size(); level-- > 0; ) {
        LinkArrayRef my_links = _graph.get_link_array(nodeid, level);
        for (uint32_t neighbor_id : my_links) {
            if (need_new_entrypoint) {
                auto entry_levels_ref = _graph.get_levels_ref(neighbor_id);
                _graph.set_entry_node({neighbor_id, entry_levels_ref, level});
                need_new_entrypoint = false;
            }
            remove_link_to(neighbor_id, nodeid, level);
        }
        mutual_reconnect(my_links, level);
    }
    if (need_new_entrypoint) {
        typename GraphType::EntryNode entry;
        _graph.set_entry_node(entry);
    }
    _graph.remove_node(nodeid);
}

template <HnswIndexType type>
void
HnswIndex<type>::remove_document(uint32_t docid)
{
    auto nodeids = _id_mapping.get_ids(docid);
    for (auto nodeid : nodeids) {
        remove_node(nodeid);
    }
    _id_mapping.free_ids(docid);
}

template <HnswIndexType type>
void
HnswIndex<type>::assign_generation(generation_t current_gen)
{
    // Note: RcuVector transfers hold lists as part of reallocation based on current generation.
    //       We need to set the next generation here, as it is incremented on a higher level right after this call.
    _graph.nodes.setGeneration(current_gen + 1);
    _graph.levels_store.assign_generation(current_gen);
    _graph.links_store.assign_generation(current_gen);
    _id_mapping.assign_generation(current_gen);
}

template <HnswIndexType type>
void
HnswIndex<type>::reclaim_memory(generation_t oldest_used_gen)
{
    _graph.nodes.reclaim_memory(oldest_used_gen);
    _graph.levels_store.reclaim_memory(oldest_used_gen);
    _graph.links_store.reclaim_memory(oldest_used_gen);
    _id_mapping.reclaim_memory(oldest_used_gen);
}

template <HnswIndexType type>
void
HnswIndex<type>::compact_level_arrays(const CompactionStrategy& compaction_strategy)
{
    auto compacting_buffers = _graph.levels_store.start_compact_worst_buffers(compaction_strategy);
    uint32_t nodeid_limit = _graph.nodes.size();
    auto filter = compacting_buffers->make_entry_ref_filter();
    std::span<NodeType> nodes(&_graph.nodes[0], nodeid_limit);
    for (auto& node : nodes) {
        auto levels_ref = node.levels_ref().load_relaxed();
        if (levels_ref.valid() && filter.has(levels_ref)) {
            EntryRef new_levels_ref = _graph.levels_store.move_on_compact(levels_ref);
            node.levels_ref().store_release(new_levels_ref);
        }
    }
    compacting_buffers->finish();
}

template <HnswIndexType type>
void
HnswIndex<type>::compact_link_arrays(const CompactionStrategy& compaction_strategy)
{
    auto context = _graph.links_store.compact_worst(compaction_strategy);
    uint32_t nodeid_limit = _graph.nodes.size();
    for (uint32_t nodeid = 1; nodeid < nodeid_limit; ++nodeid) {
        EntryRef levels_ref = _graph.get_levels_ref(nodeid);
        if (levels_ref.valid()) {
            std::span<AtomicEntryRef> refs(_graph.levels_store.get_writable(levels_ref));
            context->compact(refs);
        }
    }
}

template <HnswIndexType type>
bool
HnswIndex<type>::consider_compact(const CompactionStrategy& compaction_strategy)
{
    bool result = false;
    if (_graph.levels_store.consider_compact()) {
        compact_level_arrays(compaction_strategy);
        result = true;
    }
    if (_graph.links_store.consider_compact()) {
        compact_link_arrays(compaction_strategy);
        result = true;
    }
    if (_id_mapping.consider_compact()) {
        _id_mapping.compact_worst(compaction_strategy);
        result = true;
    }
    return result;
}

template <HnswIndexType type>
vespalib::MemoryUsage
HnswIndex<type>::update_stat(const CompactionStrategy& compaction_strategy)
{
    vespalib::MemoryUsage result;
    result.merge(_graph.nodes.getMemoryUsage());
    result.merge(_graph.levels_store.update_stat(compaction_strategy));
    result.merge(_graph.links_store.update_stat(compaction_strategy));
    result.merge(_id_mapping.update_stat(compaction_strategy));
    return result;
}

template <HnswIndexType type>
vespalib::MemoryUsage
HnswIndex<type>::memory_usage() const
{
    vespalib::MemoryUsage result;
    result.merge(_graph.nodes.getMemoryUsage());
    result.merge(_graph.levels_store.getMemoryUsage());
    result.merge(_graph.links_store.getMemoryUsage());
    result.merge(_id_mapping.memory_usage());
    return result;
}

template <HnswIndexType type>
void
HnswIndex<type>::populate_address_space_usage(search::AddressSpaceUsage& usage) const
{
    usage.set(AddressSpaceComponents::hnsw_levels_store, _graph.levels_store.addressSpaceUsage());
    usage.set(AddressSpaceComponents::hnsw_links_store, _graph.links_store.addressSpaceUsage());
    if constexpr (type == HnswIndexType::MULTI) {
        usage.set(AddressSpaceComponents::hnsw_nodeid_mapping, _id_mapping.address_space_usage());
    }
}

template <HnswIndexType type>
std::unique_ptr<vespalib::StateExplorer>
HnswIndex<type>::make_state_explorer() const
{
    return std::make_unique<HnswIndexExplorer<type>>(*this);
}

template <HnswIndexType type>
void
HnswIndex<type>::shrink_lid_space(uint32_t doc_id_limit)
{
    assert(doc_id_limit >= 1u);
    if constexpr (std::is_same_v<IdMapping, HnswIdentityMapping>) {
        assert(doc_id_limit >= _graph.nodes_size.load(std::memory_order_relaxed));
        uint32_t old_doc_id_limit = _graph.nodes.size();
        if (doc_id_limit >= old_doc_id_limit) {
            return;
        }
        _graph.nodes.shrink(doc_id_limit);
    }
}

template <HnswIndexType type>
std::unique_ptr<NearestNeighborIndexSaver>
HnswIndex<type>::make_saver(GenericHeader& header) const
{
    save_mips_max_distance(header, distance_function_factory());
    return std::make_unique<HnswIndexSaver<type>>(_graph);
}

template <HnswIndexType type>
std::unique_ptr<NearestNeighborIndexLoader>
HnswIndex<type>::make_loader(FastOS_FileInterface& file, const vespalib::GenericHeader& header)
{
    assert(get_entry_nodeid() == 0); // cannot load after index has data
    load_mips_max_distance(header, distance_function_factory());
    using ReaderType = FileReader<uint32_t>;
    using LoaderType = HnswIndexLoader<ReaderType, type>;
    return std::make_unique<LoaderType>(_graph, _id_mapping, std::make_unique<ReaderType>(&file));
}

struct NeighborsByDocId {
    bool operator() (const NearestNeighborIndex::Neighbor &lhs,
                     const NearestNeighborIndex::Neighbor &rhs)
    {
        return (lhs.docid < rhs.docid);
    }
};

template <HnswIndexType type>
std::vector<NearestNeighborIndex::Neighbor>
HnswIndex<type>::top_k_by_docid(Stats &stats, uint32_t k, const BoundDistanceFunction &df, const GlobalFilter *filter, bool low_hit_ratio, double exploration,
                                uint32_t explore_k, double exploration_slack, bool prefetch_tensors, const vespalib::Doom& doom, double distance_threshold) const
{
    SearchBestNeighbors candidates = top_k_candidates(stats, df, std::max(k, explore_k), exploration_slack, prefetch_tensors, filter, low_hit_ratio, exploration, doom);
    auto result = candidates.get_neighbors(k, distance_threshold);
    std::sort(result.begin(), result.end(), NeighborsByDocId());
    return result;
}

template <HnswIndexType type>
std::vector<NearestNeighborIndex::Neighbor>
HnswIndex<type>::find_top_k(Stats &stats, uint32_t k, const BoundDistanceFunction &df, uint32_t explore_k, double exploration_slack, bool prefetch_tensors,
                            const vespalib::Doom& doom, double distance_threshold) const
{
    return top_k_by_docid(stats, k, df, nullptr, false, 0.0, explore_k, exploration_slack, prefetch_tensors, doom, distance_threshold);
}

template <HnswIndexType type>
std::vector<NearestNeighborIndex::Neighbor>
HnswIndex<type>::find_top_k_with_filter(Stats &stats, uint32_t k, const BoundDistanceFunction &df, const GlobalFilter &filter, bool low_hit_ratio, double exploration,
                                        uint32_t explore_k, double exploration_slack, bool prefetch_tensors, const vespalib::Doom& doom, double distance_threshold) const
{
    return top_k_by_docid(stats, k, df, &filter, low_hit_ratio, exploration, explore_k, exploration_slack, prefetch_tensors, doom, distance_threshold);
}

template <HnswIndexType type>
typename HnswIndex<type>::SearchBestNeighbors
HnswIndex<type>::top_k_candidates(Stats &stats, const BoundDistanceFunction &df, uint32_t k, double exploration_slack, bool prefetch_tensors, const GlobalFilter *filter, bool low_hit_ratio, double exploration, const vespalib::Doom& doom) const
{
    SearchBestNeighbors best_neighbors;
    auto entry = _graph.get_entry_node();
    if (entry.nodeid == 0) {
        // graph has no entry point
        return best_neighbors;
    }
    stats.count_visited_node(); // Count entry point as visited node
    int search_level = entry.level;
    double entry_dist = calc_distance(df, entry.nodeid);
    stats.count_computed_distance();
    uint32_t entry_docid = get_docid(entry.nodeid);
    // TODO: check if entry docid/levels_ref is still valid here
    HnswCandidate entry_point(entry.nodeid, entry_docid, entry.levels_ref, entry_dist);
    while (search_level > 0) {
        entry_point = find_nearest_in_layer(stats, df, entry_point, search_level);
        --search_level;
    }
    best_neighbors.push(entry_point);
    if (filter && filter->is_active() && low_hit_ratio) {
        search_layer_filter_first(stats, df, k, exploration_slack, best_neighbors, exploration, 0, &doom, filter);
    } else {
        search_layer(stats, df, k, exploration_slack, prefetch_tensors, best_neighbors, 0, &doom, filter);
    }
    return best_neighbors;
}

template <HnswIndexType type>
HnswTestNode
HnswIndex<type>::get_node(uint32_t nodeid) const
{
    auto levels_ref = _graph.acquire_levels_ref(nodeid);
    if (!levels_ref.valid()) {
        return {};
    }
    auto levels = _graph.levels_store.get(levels_ref);
    HnswTestNode::LevelArray result;
    for (const auto& links_ref : levels) {
        auto links = _graph.links_store.get(links_ref.load_acquire());
        HnswTestNode::LinkArray result_links(links.begin(), links.end());
        std::sort(result_links.begin(), result_links.end());
        result.push_back(result_links);
    }
    return {std::move(result)};
}

template <HnswIndexType type>
void
HnswIndex<type>::set_node(uint32_t nodeid, const HnswTestNode &node)
{
    size_t num_levels = node.size();
    assert(num_levels > 0);
    auto levels_ref = _graph.make_node(nodeid, nodeid, 0, num_levels);
    for (size_t level = 0; level < num_levels; ++level) {
        connect_new_node(nodeid, node.level(level), level);
    }
    int max_level = num_levels - 1;
    if (get_entry_level() < max_level) {
        _graph.set_entry_node({nodeid, levels_ref, max_level});
    }
}

template <HnswIndexType type>
bool
HnswIndex<type>::check_link_symmetry() const
{
    bool all_sym = true;
    size_t nodeid_limit = _graph.size();
    for (size_t nodeid = 0; nodeid < nodeid_limit; ++nodeid) {
        auto levels_ref = _graph.acquire_levels_ref(nodeid);
        if (levels_ref.valid()) {
            auto levels = _graph.levels_store.get(levels_ref);
            uint32_t level = 0;
            for (const auto& links_ref : levels) {
                auto links = _graph.links_store.get(links_ref.load_acquire());
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

template <HnswIndexType type>
ReachabilityResult
HnswIndex<type>::count_reachable_nodes() const
{
    constexpr uint32_t timeout_ms = std::chrono::duration_cast<std::chrono::milliseconds>(MAX_COUNT_DURATION).count();
    uint32_t valid_nodes = _graph.get_active_nodes();
    auto entry = _graph.get_entry_node();
    int search_level = entry.level;
    if (search_level < 0) {
        return {0, valid_nodes, 0, true, timeout_ms};
    }
    std::vector<bool> visited(_graph.size());
    LinkArray found_links;
    if (entry.nodeid < visited.size()) {
        found_links.push_back(entry.nodeid);
        visited[entry.nodeid] = true;
    }
    vespalib::steady_time doom = vespalib::steady_clock::now() + MAX_COUNT_DURATION;
    while (search_level > 0) {
        for (uint32_t idx = 0; idx < found_links.size(); ++idx) {
            if (vespalib::steady_clock::now() > doom) {
                uint32_t found = found_links.size();
                // All found nodes are pending (not yet explored at level 0)
                return {found, valid_nodes - found, found, false, timeout_ms};
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
    uint32_t found_cnt = found_links.size();
    search::AllocatedBitVector visitNext(visited.size());
    for (uint32_t nodeid : found_links) {
        visitNext.setBit(nodeid);
    }
    bool runAnotherVisit = true;
    visitNext.invalidateCachedCount();
    while (runAnotherVisit) {
        if (vespalib::steady_clock::now() > doom) {
            uint32_t pending = visitNext.countTrueBits();
            return {found_cnt, valid_nodes - found_cnt, pending, false, timeout_ms};
        }
        runAnotherVisit = false;
        visitNext.foreach_truebit(
                [&] (uint32_t nodeid) {
                    // note: search_level == 0
                    auto neighbors = _graph.acquire_link_array(nodeid, 0);
                    for (uint32_t neighbor : neighbors) {
                        if (neighbor >= visited.size() || visited[neighbor]) {
                            continue;
                        }
                        ++found_cnt;
                        visited[neighbor] = true;
                        visitNext.setBit(neighbor);
                        runAnotherVisit = true;
                    }
                    visitNext.clearBit(nodeid);
                }
            );
    }
    return {found_cnt, valid_nodes - found_cnt, 0, true, timeout_ms};
}

template <HnswIndexType type>
uint32_t
HnswIndex<type>::get_subspaces(uint32_t docid) const noexcept
{
    if constexpr (type == HnswIndexType::SINGLE) {
        return (docid < _graph.nodes.get_size() && _graph.nodes.get_elem_ref(docid).levels_ref().load_relaxed().valid()) ? 1 : 0;
    } else {
        return _id_mapping.get_ids(docid).size();
    }
}

template <HnswIndexType type>
uint32_t
HnswIndex<type>::check_consistency(uint32_t docid_limit) const noexcept
{
    uint32_t inconsistencies = 0;
    for (uint32_t docid = 1; docid < docid_limit; ++docid) {
        auto index_subspaces = get_subspaces(docid);
        auto store_subspaces = get_vectors(docid).subspaces();
        if (index_subspaces != store_subspaces) {
            ++inconsistencies;
        }
    }
    return inconsistencies;
}

template class HnswIndex<HnswIndexType::SINGLE>;
template class HnswIndex<HnswIndexType::MULTI>;

} // namespace
