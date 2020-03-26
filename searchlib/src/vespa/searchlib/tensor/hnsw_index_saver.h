// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nearest_neighbor_index_saver.h"
#include <vespa/vespalib/datastore/entryref.h>
#include <vector>

namespace search::tensor {

class HnswGraph;

class HnswIndexSaver : public NearestNeighborIndexSaver {
public:
    using LevelVector = std::vector<search::datastore::EntryRef>;

    struct MetaData {
        uint32_t entry_docid;
        int32_t  entry_level;
        std::vector<LevelVector> nodes;
        MetaData() : entry_docid(0), entry_level(-1), nodes() {}
    };

    HnswIndexSaver(const HnswGraph &graph);
    ~HnswIndexSaver();
    void save(BufferWriter& writer) const override;

private:
    const HnswGraph &_graph;
    MetaData _meta_data;

};

}
