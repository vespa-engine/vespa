// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "nns_index_iterator.h"
#include <vespa/searchlib/tensor/nearest_neighbor_index.h>
#include <cmath>

using Neighbor = search::tensor::NearestNeighborIndex::Neighbor;

namespace search::queryeval {

/**
 * Search iterator for K nearest neighbor matching,
 * where the actual search is done up front and this class
 * just iterates over a vector held by the blueprint.
 **/
class NeighborVectorIterator : public NnsIndexIterator
{
private:
    fef::TermFieldMatchData &_tfmd;
    const std::vector<Neighbor> &_hits;
    const search::tensor::DistanceFunction &_dist_fun;
    uint32_t _idx;
    double _last_abstract_dist;
public:
    NeighborVectorIterator(fef::TermFieldMatchData &tfmd,
                           const std::vector<Neighbor> &hits,
                           const search::tensor::DistanceFunction &dist_fun)
        : _tfmd(tfmd),
          _hits(hits),
          _dist_fun(dist_fun),
          _idx(0),
          _last_abstract_dist(0.0)
    {}

    void initRange(uint32_t begin_id, uint32_t end_id) override {
        SearchIterator::initRange(begin_id, end_id);
        _idx = 0;
    }

    void doSeek(uint32_t docId) override {
        while (_idx < _hits.size()) {
            uint32_t hit_id = _hits[_idx].docid;
            if (hit_id < docId) {
                ++_idx;
            } else if (hit_id < getEndId()) {
                setDocId(hit_id);
                _last_abstract_dist = _hits[_idx].distance;
                return;
            } else {
                _idx = _hits.size();
            }
        }
        setAtEnd();
    }

    void doUnpack(uint32_t docId) override {
        double score = _dist_fun.to_rawscore(_last_abstract_dist);
        _tfmd.setRawScore(docId, score);
    }

    Trinary is_strict() const override { return Trinary::True; }
};

std::unique_ptr<NnsIndexIterator>
NnsIndexIterator::create(
        fef::TermFieldMatchData &tfmd,
        const std::vector<Neighbor> &hits,
        const search::tensor::DistanceFunction &dist_fun)
{
    return std::make_unique<NeighborVectorIterator>(tfmd, hits, dist_fun);
}

} // namespace
