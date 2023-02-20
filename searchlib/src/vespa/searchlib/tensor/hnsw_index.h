// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_config.h"
#include "distance_function.h"
#include "doc_vector_access.h"
#include "hnsw_identity_mapping.h"
#include "hnsw_index_utils.h"
#include "hnsw_multi_best_neighbors.h"
#include "hnsw_nodeid_mapping.h"
#include "hnsw_single_best_neighbors.h"
#include "hnsw_test_node.h"
#include "nearest_neighbor_index.h"
#include "random_level_generator.h"
#include "hnsw_graph.h"
#include "vector_bundle.h"
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/compaction_spec.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/stllike/allocator.h>

namespace search::tensor {

/**
 * Implementation of a hierarchical navigable small world graph (HNSW)
 * that is used for approximate K-nearest neighbor search.
 *
 * The implementation supports 1 write thread and multiple search threads without the use of mutexes.
 * This is achieved by using data stores that use generation tracking and associated memory management.
 *
 * The implementation is mainly based on the algorithms described in
 * "Efficient and robust approximate nearest neighbor search using Hierarchical Navigable Small World graphs" (Yu. A. Malkov, D. A. Yashunin),
 * but some adjustments are made to support proper removes.
 *
 * TODO: Add details on how to handle removes.
 */

namespace internal {
struct PreparedAddNode {
    using Links = std::vector<std::pair<uint32_t, vespalib::datastore::EntryRef>>;
    std::vector<Links> connections;

    PreparedAddNode() noexcept;
    explicit PreparedAddNode(std::vector<Links>&& connections_in) noexcept;
    ~PreparedAddNode();
    PreparedAddNode(PreparedAddNode&& other) noexcept;
};

struct PreparedFirstAddDoc : public PrepareResult {};

struct PreparedAddDoc : public PrepareResult {
    using ReadGuard = vespalib::GenerationHandler::Guard;
    uint32_t docid;
    ReadGuard read_guard;
    std::vector<PreparedAddNode> nodes;
    PreparedAddDoc(uint32_t docid_in, ReadGuard read_guard_in) noexcept;
    ~PreparedAddDoc();
    PreparedAddDoc(PreparedAddDoc&& other) noexcept;
};
}
template <HnswIndexType type>
class HnswIndex : public NearestNeighborIndex {
public:
    uint32_t get_docid(uint32_t nodeid) const {
        if constexpr (NodeType::identity_mapping) {
            return nodeid;
        } else {
            return _graph.nodes.acquire_elem_ref(nodeid).acquire_docid();
        }
    }

    static constexpr HnswIndexType index_type = type;
    using SearchBestNeighbors = typename HnswIndexTraits<type>::SearchBestNeighbors;
    using IdMapping = typename HnswIndexTraits<type>::IdMapping;
protected:
    using GraphType = HnswGraph<type>;
    using NodeType = typename GraphType::NodeType;
    using AtomicEntryRef = vespalib::datastore::AtomicEntryRef;
    using LevelArrayStore = typename GraphType::LevelArrayStore;

    using LinkArrayStore = typename GraphType::LinkArrayStore;
    using LinkArrayRef = typename GraphType::LinkArrayRef;
    using LinkArray = std::vector<uint32_t, vespalib::allocator_large<uint32_t>>;

    using LevelArrayRef = typename GraphType::LevelArrayRef;

    using TypedCells = vespalib::eval::TypedCells;

    static uint32_t acquire_docid(const NodeType& node, uint32_t nodeid) {
        if constexpr (NodeType::identity_mapping) {
            return nodeid;
        } else {
            return node.acquire_docid();
        }
    }

    // Clamp level generator member function max_level() return value
    // Chosen value is based on class comment for InvLogLevelGenerator.
    static constexpr uint32_t max_max_level = 29;

    GraphType _graph;
    const DocVectorAccess& _vectors;
    DistanceFunction::UP _distance_func;
    RandomLevelGenerator::UP _level_generator;
    IdMapping _id_mapping; // mapping from docid to nodeid vector
    HnswIndexConfig _cfg;

    uint32_t max_links_for_level(uint32_t level) const;
    void add_link_to(uint32_t nodeid, uint32_t level, const LinkArrayRef& old_links, uint32_t new_link) {
        LinkArray new_links(old_links.begin(), old_links.end());
        new_links.push_back(new_link);
        _graph.set_link_array(nodeid, level, new_links);
    }

    /**
     * Returns true if the distance between the candidate and a node in the current result
     * is less than the distance between the candidate and the node we want to add to the graph.
     * In this case the candidate should be discarded as we already are connected to the space
     * where the candidate is located.
     * Used by select_neighbors_heuristic().
     */
    bool have_closer_distance(HnswTraversalCandidate candidate, const HnswTraversalCandidateVector& curr_result) const;
    struct SelectResult {
        HnswTraversalCandidateVector used;
        LinkArray unused;
        ~SelectResult() {}
    };
    template <typename HnswCandidateVectorT>
    SelectResult select_neighbors_heuristic(const HnswCandidateVectorT& neighbors, uint32_t max_links) const;
    template <typename HnswCandidateVectorT>
    SelectResult select_neighbors_simple(const HnswCandidateVectorT& neighbors, uint32_t max_links) const;
    template <typename HnswCandidateVectorT>
    SelectResult select_neighbors(const HnswCandidateVectorT& neighbors, uint32_t max_links) const;
    void shrink_if_needed(uint32_t nodeid, uint32_t level);
    void connect_new_node(uint32_t nodeid, const LinkArrayRef &neighbors, uint32_t level);
    void mutual_reconnect(const LinkArrayRef &cluster, uint32_t level);
    void remove_link_to(uint32_t remove_from, uint32_t remove_id, uint32_t level);

    TypedCells get_vector(uint32_t nodeid) const {
        if constexpr (NodeType::identity_mapping) {
            return _vectors.get_vector(nodeid, 0);
        } else {
            auto& ref = _graph.nodes.acquire_elem_ref(nodeid);
            uint32_t docid = ref.acquire_docid();
            uint32_t subspace = ref.acquire_subspace();
            return _vectors.get_vector(docid, subspace);
        }
    }
    TypedCells get_vector(uint32_t docid, uint32_t subspace) const {
        return _vectors.get_vector(docid, subspace);
    }
    VectorBundle get_vectors(uint32_t docid) const {
        return _vectors.get_vectors(docid);
    }

    double calc_distance(uint32_t lhs_nodeid, uint32_t rhs_nodeid) const;
    double calc_distance(const TypedCells& lhs, uint32_t rhs_nodeid) const;
    double calc_distance(const TypedCells& lhs, uint32_t rhs_docid, uint32_t rhs_subspace) const;
    uint32_t estimate_visited_nodes(uint32_t level, uint32_t nodeid_limit, uint32_t neighbors_to_find, const GlobalFilter* filter) const;

    /**
     * Performs a greedy search in the given layer to find the candidate that is nearest the input vector.
     */
    HnswCandidate find_nearest_in_layer(const TypedCells& input, const HnswCandidate& entry_point, uint32_t level) const;
    template <class VisitedTracker, class BestNeighbors>
    void search_layer_helper(const TypedCells& input, uint32_t neighbors_to_find, BestNeighbors& best_neighbors,
                             uint32_t level, const GlobalFilter *filter,
                             uint32_t nodeid_limit,
                             uint32_t estimated_visited_nodes) const;
    template <class BestNeighbors>
    void search_layer(const TypedCells& input, uint32_t neighbors_to_find, BestNeighbors& best_neighbors,
                      uint32_t level, const GlobalFilter *filter = nullptr) const;
    std::vector<Neighbor> top_k_by_docid(uint32_t k, TypedCells vector,
                                         const GlobalFilter *filter, uint32_t explore_k,
                                         double distance_threshold) const;

    internal::PreparedAddDoc internal_prepare_add(uint32_t docid, VectorBundle input_vectors,
                                        vespalib::GenerationHandler::Guard read_guard) const;
    void internal_prepare_add_node(internal::PreparedAddDoc& op, TypedCells input_vector, const typename GraphType::EntryNode& entry) const;
    LinkArray filter_valid_nodeids(uint32_t level, const internal::PreparedAddNode::Links &neighbors, uint32_t self_nodeid);
    void internal_complete_add(uint32_t docid, internal::PreparedAddDoc &op);
    void internal_complete_add_node(uint32_t nodeid, uint32_t docid, uint32_t subspace, internal::PreparedAddNode &prepared_node);
public:
    HnswIndex(const DocVectorAccess& vectors, DistanceFunction::UP distance_func,
              RandomLevelGenerator::UP level_generator, const HnswIndexConfig& cfg);
    ~HnswIndex() override;

    const HnswIndexConfig& config() const { return _cfg; }

    // Implements NearestNeighborIndex
    void add_document(uint32_t docid) override;
    std::unique_ptr<PrepareResult> prepare_add_document(uint32_t docid,
                                                        VectorBundle vectors,
                                                        vespalib::GenerationHandler::Guard read_guard) const override;
    void complete_add_document(uint32_t docid, std::unique_ptr<PrepareResult> prepare_result) override;
    void remove_node(uint32_t nodeid);
    void remove_document(uint32_t docid) override;
    void assign_generation(generation_t current_gen) override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    void compact_level_arrays(const CompactionStrategy& compaction_strategy);
    void compact_link_arrays(const CompactionStrategy& compaction_strategy);
    bool consider_compact(const CompactionStrategy& compaction_strategy) override;
    vespalib::MemoryUsage update_stat(const CompactionStrategy& compaction_strategy) override;
    vespalib::MemoryUsage memory_usage() const override;
    void populate_address_space_usage(search::AddressSpaceUsage& usage) const override;
    void get_state(const vespalib::slime::Inserter& inserter) const override;
    void shrink_lid_space(uint32_t doc_id_limit) override;

    std::unique_ptr<NearestNeighborIndexSaver> make_saver() const override;
    std::unique_ptr<NearestNeighborIndexLoader> make_loader(FastOS_FileInterface& file) override;

    std::vector<Neighbor> find_top_k(uint32_t k, TypedCells vector, uint32_t explore_k,
                                     double distance_threshold) const override;
    std::vector<Neighbor> find_top_k_with_filter(uint32_t k, TypedCells vector,
                                                 const GlobalFilter &filter, uint32_t explore_k,
                                                 double distance_threshold) const override;
    const DistanceFunction *distance_function() const override { return _distance_func.get(); }

    SearchBestNeighbors top_k_candidates(const TypedCells &vector, uint32_t k, const GlobalFilter *filter) const;

    uint32_t get_entry_nodeid() const { return _graph.get_entry_node().nodeid; }
    int32_t get_entry_level() const { return _graph.get_entry_node().level; }

    // Should only be used by unit tests.
    HnswTestNode get_node(uint32_t nodeid) const;
    void set_node(uint32_t nodeid, const HnswTestNode &node);
    bool check_link_symmetry() const;
    std::pair<uint32_t, bool> count_reachable_nodes() const;
    GraphType& get_graph() { return _graph; }
    IdMapping& get_id_mapping() { return _id_mapping; }

    static vespalib::datastore::ArrayStoreConfig make_default_level_array_store_config();
    static vespalib::datastore::ArrayStoreConfig make_default_link_array_store_config();
};

}
