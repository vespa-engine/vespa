// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_multi_best_neighbors.h"

namespace search::tensor {

HnswMultiBestNeighbors::~HnswMultiBestNeighbors() = default;

std::vector<NearestNeighborIndex::Neighbor>
HnswMultiBestNeighbors::get_neighbors(uint32_t k, double distance_threshold)
{
    while (_docids.size() > k) {
        pop();
    }
    std::vector<NearestNeighborIndex::Neighbor> result;
    result.reserve(_docids.size());
    while (!_candidates.empty()) {
        auto& hit = _candidates.top();
        uint32_t docid = hit.docid;
        if (remove_docid(docid) && (!(hit.distance > distance_threshold))) {
            result.emplace_back(docid, hit.distance);
        }
        _candidates.pop();
    }
    return result;
}

}
