// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reusable_set_visited_tracker.h"
#include "hnsw_index.h"

namespace search::tensor {

ReusableSetVisitedTracker::ReusableSetVisitedTracker(const HnswIndex& index, uint32_t doc_id_limit, uint32_t)
    : _visited(index.get_visited_set_pool().get(doc_id_limit))
{
}

ReusableSetVisitedTracker::~ReusableSetVisitedTracker() = default;

}
