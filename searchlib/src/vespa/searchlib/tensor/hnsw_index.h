// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_config.h"
#include "distance_function.h"
#include "doc_vector_access.h"
#include "hnsw_identity_mapping.h"
#include "hnsw_index_utils.h"
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
class HnswIndex : public NearestNeighborIndex {
public:
    class HnswIndexCompactionSpec {
        CompactionSpec _level_arrays;
        CompactionSpec _link_arrays;

    public:
        HnswIndexCompactionSpec()
            : _level_arrays(),
              _link_arrays()
        {
        }
        HnswIndexCompactionSpec(CompactionSpec level_arrays_, CompactionSpec link_arrays_)
            : _level_arrays(level_arrays_),
              _link_arrays(link_arrays_)
        {
        }
        CompactionSpec level_arrays() const noexcept { return _level_arrays; }
        CompactionSpec link_arrays() const noexcept { return _link_arrays; }
    };

    uint32_t get_docid(uint32_t nodeid) const {
        if constexpr (NodeType::identity_mapping) {
            return nodeid;
        } else {
            return _graph.node_refs.acquire_elem_ref(nodeid).acquire_docid();
        }
    }

    using IdMapping = HnswIdentityMapping;
protected:
    using NodeType = HnswGraph::NodeType;
    using AtomicEntryRef = HnswGraph::AtomicEntryRef;
    using NodeStore = HnswGraph::NodeStore;

    using LinkStore = HnswGraph::LinkStore;
    using LinkArrayRef = HnswGraph::LinkArrayRef;
    using LinkArray = std::vector<uint32_t, vespalib::allocator_large<uint32_t>>;

    using LevelArrayRef = HnswGraph::LevelArrayRef;

    using TypedCells = vespalib::eval::TypedCells;

    HnswGraph _graph;
    const DocVectorAccess& _vectors;
    DistanceFunction::UP _distance_func;
    RandomLevelGenerator::UP _level_generator;
    IdMapping _id_mapping; // mapping from docid to nodeid vector
    HnswIndexConfig _cfg;
    HnswIndexCompactionSpec _compaction_spec;

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
    bool have_closer_distance(HnswCandidate candidate, const HnswCandidateVector& curr_result) const;
    struct SelectResult {
        HnswCandidateVector used;
        LinkArray unused;
        ~SelectResult() {}
    };
    SelectResult select_neighbors_heuristic(const HnswCandidateVector& neighbors, uint32_t max_links) const;
    SelectResult select_neighbors_simple(const HnswCandidateVector& neighbors, uint32_t max_links) const;
    SelectResult select_neighbors(const HnswCandidateVector& neighbors, uint32_t max_links) const;
    void shrink_if_needed(uint32_t nodeid, uint32_t level);
    void connect_new_node(uint32_t nodeid, const LinkArrayRef &neighbors, uint32_t level);
    void mutual_reconnect(const LinkArrayRef &cluster, uint32_t level);
    void remove_link_to(uint32_t remove_from, uint32_t remove_id, uint32_t level);

    inline TypedCells get_vector(uint32_t nodeid) const {
        if constexpr (NodeType::identity_mapping) {
            return _vectors.get_vector(nodeid, 0);
        } else {
            auto& ref = _graph.node_refs.acquire_elem_ref(nodeid);
            uint32_t docid = ref.acquire_docid();
            uint32_t subspace = ref.acquire_subspace();
            return _vectors.get_vector(docid, subspace);
        }
    }
    inline VectorBundle get_vector_by_docid(uint32_t docid) const {
        return _vectors.get_vectors(docid);
    }

    double calc_distance(uint32_t lhs_nodeid, uint32_t rhs_nodeid) const;
    double calc_distance(const TypedCells& lhs, uint32_t rhs_nodeid) const;
    uint32_t estimate_visited_nodes(uint32_t level, uint32_t nodeid_limit, uint32_t neighbors_to_find, const GlobalFilter* filter) const;

    /**
     * Performs a greedy search in the given layer to find the candidate that is nearest the input vector.
     */
    HnswCandidate find_nearest_in_layer(const TypedCells& input, const HnswCandidate& entry_point, uint32_t level) const;
    template <class VisitedTracker>
    void search_layer_helper(const TypedCells& input, uint32_t neighbors_to_find, FurthestPriQ& found_neighbors,
                             uint32_t level, const GlobalFilter *filter,
                             uint32_t nodeid_limit,
                             uint32_t estimated_visited_nodes) const;
    void search_layer(const TypedCells& input, uint32_t neighbors_to_find, FurthestPriQ& found_neighbors,
                      uint32_t level, const GlobalFilter *filter = nullptr) const;
    std::vector<Neighbor> top_k_by_docid(uint32_t k, TypedCells vector,
                                         const GlobalFilter *filter, uint32_t explore_k,
                                         double distance_threshold) const;

    struct PreparedAddNode {
        using Links = std::vector<std::pair<uint32_t, HnswGraph::NodeRef>>;
        std::vector<Links> connections;

        PreparedAddNode() noexcept
            : connections()
        {
        }
        PreparedAddNode(std::vector<Links>&& connections_in) noexcept
            : connections(std::move(connections_in))
        {
        }
        ~PreparedAddNode() = default;
        PreparedAddNode(PreparedAddNode&& other) noexcept = default;
    };

    struct PreparedFirstAddDoc : public PrepareResult {};

    struct PreparedAddDoc : public PrepareResult {
        using ReadGuard = vespalib::GenerationHandler::Guard;
        uint32_t docid;
        ReadGuard read_guard;
        std::vector<PreparedAddNode> nodes;
        PreparedAddDoc(uint32_t docid_in, ReadGuard read_guard_in)
          : docid(docid_in),
            read_guard(std::move(read_guard_in)),
            nodes()
        {}
        ~PreparedAddDoc() = default;
        PreparedAddDoc(PreparedAddDoc&& other) = default;
    };
    PreparedAddDoc internal_prepare_add(uint32_t docid, VectorBundle input_vectors,
                                        vespalib::GenerationHandler::Guard read_guard) const;
    void internal_prepare_add_node(HnswIndex::PreparedAddDoc& op, TypedCells input_vector, const HnswGraph::EntryNode& entry) const;
    LinkArray filter_valid_nodeids(uint32_t level, const PreparedAddNode::Links &neighbors, uint32_t self_nodeid);
    void internal_complete_add(uint32_t docid, PreparedAddDoc &op);
    void internal_complete_add_node(uint32_t nodeid, uint32_t docid, uint32_t subspace, PreparedAddNode &prepared_node);
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
    void compact_level_arrays(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy);
    void compact_link_arrays(CompactionSpec compaction_spec, const CompactionStrategy& compaction_strategy);
    bool consider_compact_level_arrays(const CompactionStrategy& compaction_strategy);
    bool consider_compact_link_arrays(const CompactionStrategy& compaction_strategy);
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

    FurthestPriQ top_k_candidates(const TypedCells &vector, uint32_t k, const GlobalFilter *filter) const;

    uint32_t get_entry_nodeid() const { return _graph.get_entry_node().nodeid; }
    int32_t get_entry_level() const { return _graph.get_entry_node().level; }

    // Should only be used by unit tests.
    HnswTestNode get_node(uint32_t nodeid) const;
    void set_node(uint32_t nodeid, const HnswTestNode &node);
    bool check_link_symmetry() const;
    std::pair<uint32_t, bool> count_reachable_nodes() const;
    HnswGraph& get_graph() { return _graph; }

    static vespalib::datastore::ArrayStoreConfig make_default_node_store_config();
    static vespalib::datastore::ArrayStoreConfig make_default_link_store_config();
};

}

