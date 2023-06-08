// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_utils.h"
#include "nearest_neighbor_index.h"

namespace search::tensor {

/*
 * A priority queue of best neighbors for hnsw index. Used for search
 * when hnsw index has a single node per document.
 */
class HnswSingleBestNeighbors {
    using EntryRef = vespalib::datastore::EntryRef;
    FurthestPriQ _candidates;
public:
    HnswSingleBestNeighbors()
        : _candidates()
    {
    }
    ~HnswSingleBestNeighbors() = default;

    std::vector<NearestNeighborIndex::Neighbor> get_neighbors(uint32_t k, double distance_threshold);
    void push(const HnswCandidate& candidate) { _candidates.push(candidate); }
    void pop() { _candidates.pop(); }
    const HnswCandidateVector& peek() const { return _candidates.peek(); }
    bool empty() const { return _candidates.empty(); }
    const HnswCandidate& top() const { return _candidates.top(); }
    size_t size() const { return _candidates.size(); }
    void emplace(uint32_t nodeid, uint32_t docid, EntryRef ref, double distance) { _candidates.emplace(nodeid, docid, ref, distance); }
};

}
