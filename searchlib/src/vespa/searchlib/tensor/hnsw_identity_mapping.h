// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/arrayref.h>
#include <cstdint>
#include <cassert>

namespace search::tensor {

/*
 * Class used to maintain mapping from docid to nodeid for dense tensors
 * (one node per document).
 */
class HnswIdentityMapping {
    uint32_t _nodeid;
public:
    HnswIdentityMapping()
        : _nodeid(0u)
    {
    }
    vespalib::ConstArrayRef<uint32_t> allocate_ids(uint32_t docid, uint32_t subspaces) {
        assert(subspaces == 1u);
        _nodeid = docid;
        return {&_nodeid, 1};
    }
    vespalib::ConstArrayRef<uint32_t> get_ids(uint32_t docid) {
        _nodeid = docid;
        return {&_nodeid, 1};
    }
    void free_ids(uint32_t docid) { (void) docid; }
};

}
