// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvector_visited_tracker.h"

namespace search::tensor {

BitVectorVisitedTracker::BitVectorVisitedTracker(uint32_t nodeid_limit, uint32_t)
    : _visited(nodeid_limit)
{
}

BitVectorVisitedTracker::~BitVectorVisitedTracker() = default;

}
