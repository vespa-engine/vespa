// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nearest_neighbor_index_saver.h"
#include "hnsw_graph.h"
#include <vespa/vespalib/datastore/entryref.h>
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
    using LevelVector = std::vector<search::datastore::EntryRef>;

    HnswIndexSaver(const HnswGraph &graph);
    ~HnswIndexSaver();
    void save(BufferWriter& writer) const override;

private:
    struct MetaData {
        uint32_t entry_docid;
        int32_t  entry_level;
        std::vector<LevelVector> nodes;
        MetaData() : entry_docid(0), entry_level(-1), nodes() {}
    };
    const HnswGraph::LinkStore &_graph_links;
    MetaData _meta_data;
};

}
