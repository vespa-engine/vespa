// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/allocatedbitvector.h>

namespace search::tensor {

/*
 * Tracker for visited nodes based on search::AllocatedBitVector. Best when
 * many nodes are visited.
 */
class BitVectorVisitedTracker
{
    search::AllocatedBitVector _visited;
public:
    BitVectorVisitedTracker(uint32_t nodeid_limit, uint32_t);
    ~BitVectorVisitedTracker();
    void mark(uint32_t nodeid) { _visited.setBit(nodeid); }
    bool try_mark(uint32_t nodeid) {
        if (_visited.testBit(nodeid)) {
            return false;
        } else {
            _visited.setBit(nodeid);
            return true;
        }
    }
};

}
