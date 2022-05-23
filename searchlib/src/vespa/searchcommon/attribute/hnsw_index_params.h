// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "distance_metric.h"

namespace search::attribute {

/**
 * Configuration parameters for a hnsw index used together with a 1-dimensional indexed tensor
 * for approximate nearest neighbor search.
 */
class HnswIndexParams {
private:
    uint32_t _max_links_per_node;
    uint32_t _neighbors_to_explore_at_insert;
    // This is always the same as in the attribute config, and is duplicated here to simplify usage.
    DistanceMetric _distance_metric;
    bool _multi_threaded_indexing;

public:
    HnswIndexParams(uint32_t max_links_per_node_in,
                    uint32_t neighbors_to_explore_at_insert_in,
                    DistanceMetric distance_metric_in,
                    bool multi_threaded_indexing_in = false) noexcept
            : _max_links_per_node(max_links_per_node_in),
              _neighbors_to_explore_at_insert(neighbors_to_explore_at_insert_in),
              _distance_metric(distance_metric_in),
              _multi_threaded_indexing(multi_threaded_indexing_in)
    {}

    uint32_t max_links_per_node() const { return _max_links_per_node; }
    uint32_t neighbors_to_explore_at_insert() const { return _neighbors_to_explore_at_insert; }
    DistanceMetric distance_metric() const { return _distance_metric; }
    bool multi_threaded_indexing() const { return _multi_threaded_indexing; }

    bool operator==(const HnswIndexParams& rhs) const {
        return (_max_links_per_node == rhs._max_links_per_node &&
                _neighbors_to_explore_at_insert == rhs._neighbors_to_explore_at_insert &&
                _distance_metric == rhs._distance_metric &&
                _multi_threaded_indexing == rhs._multi_threaded_indexing);
    }
};

}
