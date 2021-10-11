// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/distributor/operations/idealstate/mergemetadata.h>
#include <vector>

namespace storage::distributor {

class MergeLimiter {
    uint16_t _maxNodes;

public:
    typedef std::vector<MergeMetaData> NodeArray;

    MergeLimiter(uint16_t maxNodes);

    void limitMergeToMaxNodes(NodeArray&);
    uint16_t get_max_nodes() const noexcept { return _maxNodes; }
};

} // storage::distributor
