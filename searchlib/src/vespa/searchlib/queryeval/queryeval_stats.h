// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <atomic>
#include <cstddef>

namespace search::queryeval {

/**
 * Class for collecting statistics within blueprints and search iterators.
 * Thread-safe such that search iterators from different threads can write their collected statistics here.
 **/
class QueryEvalStats {
private:
    std::atomic<size_t> _exact_nns_distances_computed;
    std::atomic<size_t> _approximate_nns_distances_computed;
    std::atomic<size_t> _approximate_nns_nodes_visited;
public:
    QueryEvalStats() noexcept
        : _exact_nns_distances_computed(0),
          _approximate_nns_distances_computed(0),
          _approximate_nns_nodes_visited(0) {}
    size_t exact_nns_distances_computed() const noexcept { return _exact_nns_distances_computed; }
    void add_to_exact_nns_distances_computed(size_t value) noexcept { _exact_nns_distances_computed += value; }

    size_t approximate_nns_distances_computed() const noexcept { return _approximate_nns_distances_computed; }
    void add_to_approximate_nns_distances_computed(size_t value) noexcept { _approximate_nns_distances_computed += value; }

    size_t approximate_nns_nodes_visited() const noexcept { return _approximate_nns_nodes_visited; }
    void add_to_approximate_nns_nodes_visited(size_t value) noexcept { _approximate_nns_nodes_visited += value; }
};

}
