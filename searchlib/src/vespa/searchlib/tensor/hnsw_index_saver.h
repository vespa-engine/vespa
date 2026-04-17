// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nearest_neighbor_index_saver.h"
#include "hnsw_graph.h"
#include "hnsw_index_saver_metadata_node.h"
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vespa/vespalib/util/generation_guard.h>
#include <chrono>
#include <vector>

namespace search::tensor {

/**
 * Implements saving of HNSW graph structure in binary format.
 * The constructor takes a snapshot of all meta-data, but
 * the links will be fetched from the graph in the save()
 * method.
 **/
template <HnswIndexType type>
class HnswIndexSaver : public NearestNeighborIndexSaver {
public:
    HnswIndexSaver(const HnswGraph<type> &graph);
    ~HnswIndexSaver() override;
    void save(BufferWriter& writer) const override;

private:
    struct Metadata {
        using EntryRef = vespalib::datastore::EntryRef;
        using RefVector = std::vector<EntryRef, vespalib::allocator_large<EntryRef>>;
        using NodeVector = std::vector<HnswIndexSaverMetadataNode<type>, vespalib::allocator_large<HnswIndexSaverMetadataNode<type>>>;
        uint32_t   entry_nodeid;
        int32_t    entry_level;
        RefVector  refs;
        NodeVector nodes;
        Metadata();
        ~Metadata();
    };
    using LinkArrayStore = typename HnswGraph<type>::LinkArrayStore;
    const LinkArrayStore&                 _graph_links;
    Metadata                              _metadata;
    vespalib::GenerationGuard             _guard;
    std::chrono::steady_clock::time_point _index_flush_start_time;
    const HnswGraph<type>&                _graph;
};

}
