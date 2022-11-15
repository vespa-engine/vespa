// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hash_set_visited_tracker.h"

namespace search::tensor {

HashSetVisitedTracker::HashSetVisitedTracker(uint32_t, uint32_t estimated_visited_nodes)
    : _visited(estimated_visited_nodes)
{
}

HashSetVisitedTracker::~HashSetVisitedTracker() = default;

}
