// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_utils.h"
#include "hnsw_node.h"
#include "nearest_neighbor_index.h"
#include <vespa/searchlib/common/bitvector.h>
#include <vespa/vespalib/datastore/array_store.h>
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace search::tensor {

class DocVectorAccess;
class RandomLevelGenerator;

/**
 * Base class for an implementation of a hierarchical navigable small world graph (HNSW)
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
class HnswIndexBase : public NearestNeighborIndex {
public:
    class Config {
    private:
        uint32_t _max_links_at_level_0;
        uint32_t _max_links_at_hierarchic_levels;
        uint32_t _neighbors_to_explore_at_construction;
        bool _heuristic_select_neighbors;

    public:
        Config(uint32_t max_links_at_level_0_in,
               uint32_t max_links_at_hierarchic_levels_in,
               uint32_t neighbors_to_explore_at_construction_in,
               bool heuristic_select_neighbors_in)
            : _max_links_at_level_0(max_links_at_level_0_in),
              _max_links_at_hierarchic_levels(max_links_at_hierarchic_levels_in),
              _neighbors_to_explore_at_construction(neighbors_to_explore_at_construction_in),
              _heuristic_select_neighbors(heuristic_select_neighbors_in)
        {}
        uint32_t max_links_at_level_0() const { return _max_links_at_level_0; }
        uint32_t max_links_at_hierarchic_levels() const { return _max_links_at_hierarchic_levels; }
        uint32_t neighbors_to_explore_at_construction() const { return _neighbors_to_explore_at_construction; }
        bool heuristic_select_neighbors() const { return _heuristic_select_neighbors; }
    };

protected:
    using EntryRef = search::datastore::EntryRef;

    // This uses 10 bits for buffer id -> 1024 buffers.
    // As we have very short arrays we get less fragmentation with fewer and larger buffers.
    using EntryRefType = search::datastore::EntryRefT<22>;

    // Provides mapping from document id -> node reference.
    // The reference is used to lookup the node data in NodeStore.
    using NodeRefVector = vespalib::RcuVector<EntryRef>;

    // This stores the level arrays for all nodes.
    // Each node consists of an array of levels (from level 0 to n) where each entry is a reference to the link array at that level.
    // TODO: Make replacing all links on a level atomically, e.g. AtomicEntryRef
    using NodeStore = search::datastore::ArrayStore<EntryRef, EntryRefType>;
    using LevelArrayRef = NodeStore::ConstArrayRef;
    using LevelArray = vespalib::Array<EntryRef>;

    // This stores all link arrays.
    // A link array consists of the document ids of the nodes a particular node is linked to.
    using LinkStore = search::datastore::ArrayStore<uint32_t, EntryRefType>;
    using LinkArrayRef = LinkStore::ConstArrayRef;
    using LinkArray = vespalib::Array<uint32_t>;

    const DocVectorAccess& _vectors;
    RandomLevelGenerator& _level_generator;
    Config _cfg;
    NodeRefVector _node_refs;
    NodeStore _nodes;
    LinkStore _links;
    uint32_t _entry_docid;
    int _entry_level;

    static search::datastore::ArrayStoreConfig make_default_node_store_config();
    static search::datastore::ArrayStoreConfig make_default_link_store_config();

    uint32_t max_links_for_level(uint32_t level) const;
    uint32_t make_node_for_document(uint32_t docid);
    LevelArrayRef get_level_array(uint32_t docid) const;
    LinkArrayRef get_link_array(uint32_t docid, uint32_t level) const;
    void set_link_array(uint32_t docid, uint32_t level, const LinkArrayRef& links);

    virtual double calc_distance(uint32_t lhs_docid, uint32_t rhs_docid) const = 0;

    /**
     * Returns true if the distance between the candidate and a node in the current result
     * is less than the distance between the candidate and the node we want to add to the graph.
     * In this case the candidate should be discarded as we already are connected to the space
     * where the candidate is located.
     * Used by select_neighbors_heuristic().
     */
    bool have_closer_distance(HnswCandidate candidate, const LinkArray& curr_result) const;
    LinkArray select_neighbors_heuristic(const HnswCandidateVector& neighbors, uint32_t max_links) const;
    LinkArray select_neighbors_simple(const HnswCandidateVector& neighbors, uint32_t max_links) const;
    LinkArray select_neighbors(const HnswCandidateVector& neighbors, uint32_t max_links) const;
    void connect_new_node(uint32_t docid, const LinkArray& neighbors, uint32_t level);

public:
    HnswIndexBase(const DocVectorAccess& vectors, RandomLevelGenerator& level_generator, const Config& cfg);
    ~HnswIndexBase() override;

    // TODO: Add support for generation handling and cleanup (transfer_hold_lists, trim_hold_lists)

    uint32_t get_entry_docid() const { return _entry_docid; }
    uint32_t get_entry_level() const { return _entry_level; }

    // Should only be used by unit tests.
    HnswNode get_node(uint32_t docid) const;

    // TODO: Implement set_node() as well for use in unit tests.
};

}

