// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    DistanceMetric _distance_metric;

public:
    HnswIndexParams(uint32_t max_links_per_node_in,
                    uint32_t neighbors_to_explore_at_insert_in,
                    DistanceMetric distance_metric_in)
            : _max_links_per_node(max_links_per_node_in),
              _neighbors_to_explore_at_insert(neighbors_to_explore_at_insert_in),
              _distance_metric(distance_metric_in)
    {}

    uint32_t max_links_per_node() const { return _max_links_per_node; }
    uint32_t neighbors_to_explore_at_insert() const { return _neighbors_to_explore_at_insert; }
    DistanceMetric distance_metric() const { return _distance_metric; }

    bool operator==(const HnswIndexParams& rhs) const {
        return (_max_links_per_node == rhs._max_links_per_node &&
                _neighbors_to_explore_at_insert == rhs._neighbors_to_explore_at_insert &&
                _distance_metric == rhs._distance_metric);
    }
};

}
