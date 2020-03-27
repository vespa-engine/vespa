// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::fileutil { class LoadedBuffer; }

namespace search::tensor {

class HnswGraph;

/**
 * Implements loading of HNSW graph structure from binary format.
 **/
class HnswIndexLoader {
public:
    HnswIndexLoader(HnswGraph &graph);
    ~HnswIndexLoader();
    bool load(const fileutil::LoadedBuffer& buf);
private:
    HnswGraph &_graph;
    const uint32_t *_ptr;
    const uint32_t *_end;
    bool _failed;
    uint32_t nextVal() {
        if (__builtin_expect((_ptr == _end), false)) {
            _failed = true;
            return 0;
        }
        return *_ptr++;
    }
};

}
