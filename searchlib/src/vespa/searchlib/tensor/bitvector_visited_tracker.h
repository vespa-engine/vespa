// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/allocatedbitvector.h>

namespace search::tensor {

class HnswIndex;

/*
 * Tracker for visited nodes based on search::AllocatedBitVector. Best when
 * many nodes are visited.
 */
class BitVectorVisitedTracker
{
    search::AllocatedBitVector _visited;
public:
    BitVectorVisitedTracker(const HnswIndex&, uint32_t doc_id_limit, uint32_t);
    ~BitVectorVisitedTracker();
    void mark(uint32_t doc_id) { _visited.setBit(doc_id); }
    bool try_mark(uint32_t doc_id) {
        if (_visited.testBit(doc_id)) {
            return false;
        } else {
            _visited.setBit(doc_id);
            return true;
        }
    }
};

}
