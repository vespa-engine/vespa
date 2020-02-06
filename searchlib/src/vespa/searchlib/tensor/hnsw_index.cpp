// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "hnsw_index.h"

namespace search::tensor {

template <typename FloatType>
double
HnswIndex<FloatType>::calc_distance(uint32_t lhs_docid, uint32_t rhs_docid) const
{
    auto lhs = get_vector(lhs_docid);
    return calc_distance(lhs, rhs_docid);
}

template <typename FloatType>
double
HnswIndex<FloatType>::calc_distance(const Vector& lhs, uint32_t rhs_docid) const
{
    // TODO: Make it possible to specify the distance function from the outside and make it hardware optimized.
    auto rhs = get_vector(rhs_docid);
    double result = 0.0;
    size_t sz = lhs.size();
    assert(sz == rhs.size());
    for (size_t i = 0; i < sz; ++i) {
        double diff = lhs[i] - rhs[i];
        result += diff * diff;
    }
    return result;
}

template <typename FloatType>
HnswCandidate
HnswIndex<FloatType>::find_nearest_in_layer(const Vector& input, const HnswCandidate& entry_point, uint32_t level)
{
    HnswCandidate nearest = entry_point;
    bool keep_searching = true;
    while (keep_searching) {
        keep_searching = false;
        for (uint32_t neighbor_docid : get_link_array(nearest.docid, level)) {
            double dist = calc_distance(input, neighbor_docid);
            if (dist < nearest.distance) {
                nearest = HnswCandidate(neighbor_docid, dist);
                keep_searching = true;
            }
        }
    }
    return nearest;
}

template <typename FloatType>
void
HnswIndex<FloatType>::search_layer(const Vector& input, uint32_t neighbors_to_find, FurthestPriQ& best_neighbors, uint32_t level)
{
    NearestPriQ candidates;
    // TODO: Add proper handling of visited set.
    auto visited = BitVector::create(_node_refs.size());
    for (const auto &entry : best_neighbors.peek()) {
        candidates.push(entry);
        visited->setBit(entry.docid);
    }
    double limit_dist = std::numeric_limits<double>::max();

    while (!candidates.empty()) {
        auto cand = candidates.top();
        if (cand.distance > limit_dist) {
            break;
        }
        candidates.pop();
        for (uint32_t neighbor_docid : get_link_array(cand.docid, level)) {
            if (visited->testBit(neighbor_docid)) {
                continue;
            }
            visited->setBit(neighbor_docid);
            double dist_to_input = calc_distance(input, neighbor_docid);
            if (dist_to_input < limit_dist) {
                candidates.emplace(neighbor_docid, dist_to_input);
                best_neighbors.emplace(neighbor_docid, dist_to_input);
                if (best_neighbors.size() > neighbors_to_find) {
                    best_neighbors.pop();
                    limit_dist = best_neighbors.top().distance;
                }
            }
        }
    }
}

template <typename FloatType>
HnswIndex<FloatType>::HnswIndex(const DocVectorAccess& vectors, RandomLevelGenerator& level_generator, const Config& cfg)
    : HnswIndexBase(vectors, level_generator, cfg)
{
}

template <typename FloatType>
HnswIndex<FloatType>::~HnswIndex() = default;

template <typename FloatType>
void
HnswIndex<FloatType>::add_document(uint32_t docid)
{
    auto input = get_vector(docid);
    _node_refs.ensure_size(docid + 1, EntryRef());
    // A document cannot be added twice.
    assert(!_node_refs[docid].valid());
    int level = make_node_for_document(docid);
    if (_entry_docid == 0) {
        _entry_docid = docid;
        _entry_level = level;
        return;
    }

    int search_level = _entry_level;
    double entry_dist = calc_distance(input, _entry_docid);
    HnswCandidate entry_point(_entry_docid, entry_dist);
    while (search_level > level) {
        entry_point = find_nearest_in_layer(input, entry_point, search_level);
        --search_level;
    }

    FurthestPriQ best_neighbors;
    best_neighbors.push(entry_point);
    search_level = std::min(level, _entry_level);

    // Insert the added document in each level it should exist in.
    while (search_level >= 0) {
        // TODO: Rename to search_level?
        search_layer(input, _cfg.neighbors_to_explore_at_construction(), best_neighbors, search_level);
        auto neighbors = select_neighbors(best_neighbors.peek(), max_links_for_level(search_level));
        connect_new_node(docid, neighbors, search_level);
        // TODO: Shrink neighbors if needed
        --search_level;
    }
    if (level > _entry_level) {
        _entry_docid = docid;
        _entry_level = level;
    }
}

template <typename FloatType>
void
HnswIndex<FloatType>::remove_document(uint32_t docid)
{
    (void) docid;
    // TODO: implement
}

}

