// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hnsw_index_utils.h"
#include "nearest_neighbor_index.h"
#include <vespa/vespalib/stllike/hash_map.h>
#include <cassert>

namespace search::tensor {

/*
 * A priority queue of best neighbors for hnsw index. Used for search
 * when hnsw index has multiple nodes per document.
 */
class HnswMultiBestNeighbors {
    using EntryRef = vespalib::datastore::EntryRef;
    FurthestPriQ _candidates;
    vespalib::hash_map<uint32_t, uint32_t> _docids;

    void add_docid(uint32_t docid) {
        auto insres = _docids.insert(std::make_pair(docid, 1));
        if (!insres.second) {
            ++insres.first->second;
        }
    }

    bool remove_docid(uint32_t docid) {
        auto itr = _docids.find(docid);
        assert(itr != _docids.end());
        if (itr->second > 1) {
            --itr->second;
            return false;
        } else {
            _docids.erase(docid);
            return true;
        }
    }
public:
    HnswMultiBestNeighbors()
        : _candidates(),
          _docids()
    {
    }
    ~HnswMultiBestNeighbors();

    std::vector<NearestNeighborIndex::Neighbor> get_neighbors(uint32_t k, double distance_threshold);

    void push(const HnswCandidate& candidate) {
        add_docid(candidate.docid);
        _candidates.push(candidate);
    }
    void pop() {
        assert(!_candidates.empty());
        uint32_t docid = _candidates.top().docid;
        remove_docid(docid);
        _candidates.pop();
    }
    const HnswCandidateVector& peek() const { return _candidates.peek(); }
    bool empty() const { return _candidates.empty(); }
    const HnswCandidate& top() const { return _candidates.top(); }
    size_t size() const { return _docids.size(); }
    void emplace(uint32_t nodeid, uint32_t docid, EntryRef ref, double distance) {
        add_docid(docid);
        _candidates.emplace(nodeid, docid, ref, distance);
    }
};

}
