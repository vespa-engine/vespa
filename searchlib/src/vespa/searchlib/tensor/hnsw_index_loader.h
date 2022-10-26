// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nearest_neighbor_index_loader.h"
#include <vespa/vespalib/util/exceptions.h>
#include <cstdint>
#include <memory>
#include <vector>

class FastOS_FileInterface;

namespace search::tensor {

struct HnswGraph;

/**
 * Implements loading of HNSW graph structure from binary format.
 **/
template <typename ReaderType>
class HnswIndexLoader : public NearestNeighborIndexLoader {
private:
    HnswGraph& _graph;
    std::unique_ptr<ReaderType> _reader;
    uint32_t _entry_nodeid;
    int32_t _entry_level;
    uint32_t _num_nodes;
    uint32_t _nodeid;
    std::vector<uint32_t> _link_array;
    bool _complete;

    void init();
    uint32_t next_int() {
        return _reader->readHostOrder();
    }

public:
    HnswIndexLoader(HnswGraph& graph, std::unique_ptr<ReaderType> reader);
    virtual ~HnswIndexLoader();
    bool load_next() override;
};

}
