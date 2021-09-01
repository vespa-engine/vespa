// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bitvector_visited_tracker.h"

namespace search::tensor {

BitVectorVisitedTracker::BitVectorVisitedTracker(const HnswIndex&, uint32_t doc_id_limit, uint32_t)
    : _visited(doc_id_limit)
{
}

BitVectorVisitedTracker::~BitVectorVisitedTracker() = default;

}
