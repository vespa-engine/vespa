// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "nearest_neighbor_index_loader.h"
#include <vespa/vespalib/util/exceptions.h>
#include <cstdint>
#include <memory>
#include <vector>

namespace search::fileutil { class LoadedBuffer; }

namespace search::tensor {

struct HnswGraph;

/**
 * Implements loading of HNSW graph structure from binary format.
 **/
class HnswIndexLoader : public NearestNeighborIndexLoader {
private:
    HnswGraph& _graph;
    std::unique_ptr<fileutil::LoadedBuffer> _buf;
    const uint32_t* _ptr;
    const uint32_t* _end;
    uint32_t _entry_docid;
    int32_t _entry_level;
    uint32_t _num_nodes;
    uint32_t _docid;
    std::vector<uint32_t> _link_array;
    bool _complete;

    void init();
    uint32_t next_int() {
        if (__builtin_expect((_ptr == _end), false)) {
            throw vespalib::IoException
                    (vespalib::IoException::createMessage("Already at the end of buffer when trying to get next int",
                                                          vespalib::IoException::CORRUPT_DATA),
                     vespalib::IoException::CORRUPT_DATA, "");
        }
        return *_ptr++;
    }

public:
    HnswIndexLoader(HnswGraph& graph, std::unique_ptr<fileutil::LoadedBuffer> buf);
    virtual ~HnswIndexLoader();
    bool load_next() override;
};

}
