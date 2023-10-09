// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/storage/distributor/operations/idealstate/mergemetadata.h>
#include <vector>

namespace storage::distributor {

class MergeLimiter {
    uint16_t _maxNodes;

public:
    using NodeArray = std::vector<MergeMetaData>;

    explicit MergeLimiter(uint16_t maxNodes);

    void limitMergeToMaxNodes(NodeArray&);
};

} // storage::distributor
