// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_single_best_neighbors.h"

namespace search::tensor {

std::vector<NearestNeighborIndex::Neighbor>
HnswSingleBestNeighbors::get_neighbors(uint32_t k, double distance_threshold)
{
    while (_candidates.size() > k) {
        _candidates.pop();
    }
    std::vector<NearestNeighborIndex::Neighbor> result;
    result.reserve(_candidates.size());
    for (const HnswCandidate & hit : _candidates.peek()) {
        if (hit.distance > distance_threshold) continue;
        result.emplace_back(hit.docid, hit.distance);
    }
    return result;
}

}
