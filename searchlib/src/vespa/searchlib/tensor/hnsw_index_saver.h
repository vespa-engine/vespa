// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nearest_neighbor_index_saver.h"
#include "hnsw_graph.h"
#include <vespa/vespalib/datastore/entryref.h>
#include <vespa/vespalib/stllike/allocator.h>
#include <vector>

namespace search::tensor {

/**
 * Implements saving of HNSW graph structure in binary format.
 * The constructor takes a snapshot of all meta-data, but
 * the links will be fetched from the graph in the save()
 * method.
 **/
class HnswIndexSaver : public NearestNeighborIndexSaver {
public:
    HnswIndexSaver(const HnswGraph &graph);
    ~HnswIndexSaver() override;
    void save(BufferWriter& writer) const override;

private:
    struct MetaData {
        using EntryRef = vespalib::datastore::EntryRef;
        uint32_t entry_nodeid;
        int32_t  entry_level;
        std::vector<EntryRef, vespalib::allocator_large<EntryRef>> refs;
        std::vector<uint32_t, vespalib::allocator_large<uint32_t>> nodes;
        MetaData();
        ~MetaData();
    };
    const HnswGraph::LinkStore &_graph_links;
    MetaData _meta_data;
};

}
