// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nearest_neighbor_index_saver.h"
#include "hnsw_graph.h"
#include "hnsw_index_saver_metadata_node.h"
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
template <HnswIndexType type>
class HnswIndexSaver : public NearestNeighborIndexSaver {
public:
    HnswIndexSaver(const HnswGraph<type> &graph);
    ~HnswIndexSaver() override;
    void save(BufferWriter& writer) const override;

private:
    struct MetaData {
        using EntryRef = vespalib::datastore::EntryRef;
        uint32_t entry_nodeid;
        int32_t  entry_level;
        std::vector<EntryRef, vespalib::allocator_large<EntryRef>> refs;
        std::vector<HnswIndexSaverMetaDataNode<type>, vespalib::allocator_large<HnswIndexSaverMetaDataNode<type>>> nodes;
        MetaData();
        ~MetaData();
    };
    const typename HnswGraph<type>::LinkArrayStore &_graph_links;
    MetaData _meta_data;
};

}
