// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_function.h"
#include "doc_vector_access.h"
#include "hnsw_index_utils.h"
#include "hnsw_node.h"
#include "nearest_neighbor_index.h"
#include "random_level_generator.h"
#include "hnsw_graph.h"
#include <vespa/eval/eval/typed_cells.h>
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/atomic_entry_ref.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/rcuvector.h>
#include <vespa/vespalib/util/reusable_set_pool.h>

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
    class Config {
    private:
        uint32_t _max_links_at_level_0;
        uint32_t _max_links_on_inserts;
        uint32_t _neighbors_to_explore_at_construction;
        uint32_t _min_size_before_two_phase;
        bool _heuristic_select_neighbors;

    public:
        Config(uint32_t max_links_at_level_0_in,
               uint32_t max_links_on_inserts_in,
               uint32_t neighbors_to_explore_at_construction_in,
               uint32_t min_size_before_two_phase_in,
               bool heuristic_select_neighbors_in)
            : _max_links_at_level_0(max_links_at_level_0_in),
              _max_links_on_inserts(max_links_on_inserts_in),
              _neighbors_to_explore_at_construction(neighbors_to_explore_at_construction_in),
              _min_size_before_two_phase(min_size_before_two_phase_in),
              _heuristic_select_neighbors(heuristic_select_neighbors_in)
        {}
        uint32_t max_links_at_level_0() const { return _max_links_at_level_0; }
        uint32_t max_links_on_inserts() const { return _max_links_on_inserts; }
        uint32_t neighbors_to_explore_at_construction() const { return _neighbors_to_explore_at_construction; }
        uint32_t min_size_before_two_phase() const { return _min_size_before_two_phase; }
        bool heuristic_select_neighbors() const { return _heuristic_select_neighbors; }
    };

protected:
    using AtomicEntryRef = HnswGraph::AtomicEntryRef;
    using NodeStore = HnswGraph::NodeStore;

    using LinkStore = HnswGraph::LinkStore;
    using LinkArrayRef = HnswGraph::LinkArrayRef;
    using LinkArray = vespalib::Array<uint32_t>;

    using LevelArrayRef = HnswGraph::LevelArrayRef;
    using LevelArray = vespalib::Array<AtomicEntryRef>;

    using TypedCells = vespalib::eval::TypedCells;

    HnswGraph _graph;
    const DocVectorAccess& _vectors;
    DistanceFunction::UP _distance_func;
    RandomLevelGenerator::UP _level_generator;
    Config _cfg;
    mutable vespalib::ReusableSetPool _visited_set_pool;

    uint32_t max_links_for_level(uint32_t level) const;
    void add_link_to(uint32_t docid, uint32_t level, const LinkArrayRef& old_links, uint32_t new_link) {
        LinkArray new_links(old_links.begin(), old_links.end());
        new_links.push_back(new_link);
        _graph.set_link_array(docid, level, new_links);
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
    void shrink_if_needed(uint32_t docid, uint32_t level);
    void connect_new_node(uint32_t docid, const LinkArrayRef &neighbors, uint32_t level);
    void mutual_reconnect(const LinkArrayRef &cluster, uint32_t level);
    void remove_link_to(uint32_t remove_from, uint32_t remove_id, uint32_t level);

    inline TypedCells get_vector(uint32_t docid) const {
        return _vectors.get_vector(docid);
    }

    double calc_distance(uint32_t lhs_docid, uint32_t rhs_docid) const;
    double calc_distance(const TypedCells& lhs, uint32_t rhs_docid) const;

    /**
     * Performs a greedy search in the given layer to find the candidate that is nearest the input vector.
     */
    HnswCandidate find_nearest_in_layer(const TypedCells& input, const HnswCandidate& entry_point, uint32_t level) const;
    void search_layer(const TypedCells& input, uint32_t neighbors_to_find, FurthestPriQ& found_neighbors,
                      uint32_t level, const search::BitVector *filter = nullptr) const;
    std::vector<Neighbor> top_k_by_docid(uint32_t k, TypedCells vector,
                                         const BitVector *filter, uint32_t explore_k,
                                         double distance_threshold) const;

    struct PreparedAddDoc : public PrepareResult {
        using ReadGuard = vespalib::GenerationHandler::Guard;
        uint32_t docid;
        int32_t max_level;
        ReadGuard read_guard;
        using Links = std::vector<std::pair<uint32_t, HnswGraph::NodeRef>>;
        std::vector<Links> connections;
        PreparedAddDoc(uint32_t docid_in, int32_t max_level_in, ReadGuard read_guard_in)
          : docid(docid_in), max_level(max_level_in),
            read_guard(std::move(read_guard_in)),
            connections(max_level+1)
        {}
        ~PreparedAddDoc() = default;
        PreparedAddDoc(PreparedAddDoc&& other) = default;
    };
    PreparedAddDoc internal_prepare_add(uint32_t docid, TypedCells input_vector,
                                        vespalib::GenerationHandler::Guard read_guard) const;
    LinkArray filter_valid_docids(uint32_t level, const PreparedAddDoc::Links &neighbors, uint32_t me);
    void internal_complete_add(uint32_t docid, PreparedAddDoc &op);
public:
    HnswIndex(const DocVectorAccess& vectors, DistanceFunction::UP distance_func,
              RandomLevelGenerator::UP level_generator, const Config& cfg);
    ~HnswIndex() override;

    const Config& config() const { return _cfg; }

    // Implements NearestNeighborIndex
    void add_document(uint32_t docid) override;
    std::unique_ptr<PrepareResult> prepare_add_document(uint32_t docid,
            TypedCells vector,
            vespalib::GenerationHandler::Guard read_guard) const override;
    void complete_add_document(uint32_t docid, std::unique_ptr<PrepareResult> prepare_result) override;
    void remove_document(uint32_t docid) override;
    void transfer_hold_lists(generation_t current_gen) override;
    void trim_hold_lists(generation_t first_used_gen) override;
    vespalib::MemoryUsage memory_usage() const override;
    void get_state(const vespalib::slime::Inserter& inserter) const override;

    std::unique_ptr<NearestNeighborIndexSaver> make_saver() const override;
    bool load(const fileutil::LoadedBuffer& buf) override;

    std::vector<Neighbor> find_top_k(uint32_t k, TypedCells vector, uint32_t explore_k,
                                     double distance_threshold) const override;
    std::vector<Neighbor> find_top_k_with_filter(uint32_t k, TypedCells vector,
                                                 const BitVector &filter, uint32_t explore_k,
                                                 double distance_threshold) const override;
    const DistanceFunction *distance_function() const override { return _distance_func.get(); }

    FurthestPriQ top_k_candidates(const TypedCells &vector, uint32_t k, const BitVector *filter) const;

    uint32_t get_entry_docid() const { return _graph.get_entry_node().docid; }
    int32_t get_entry_level() const { return _graph.get_entry_node().level; }

    // Should only be used by unit tests.
    HnswNode get_node(uint32_t docid) const;
    void set_node(uint32_t docid, const HnswNode &node);
    bool check_link_symmetry() const;
    uint32_t count_reachable_nodes() const;

    static vespalib::datastore::ArrayStoreConfig make_default_node_store_config();
    static vespalib::datastore::ArrayStoreConfig make_default_link_store_config();
};

}

