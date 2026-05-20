// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <atomic>
#include <memory>

namespace search::queryeval {

/**
 * Class for collecting statistics from query setup.
 * Not thread safe.
 **/
class QuerySetupStats {
private:
    size_t             _approximate_nns_distances_computed;
    size_t             _approximate_nns_nodes_visited;

public:
    QuerySetupStats() noexcept
        : _approximate_nns_distances_computed(0),
          _approximate_nns_nodes_visited(0) {}

    size_t approximate_nns_distances_computed() const noexcept { return _approximate_nns_distances_computed; }
    void add_to_approximate_nns_distances_computed(size_t value) noexcept {
        _approximate_nns_distances_computed += value;
    }

    size_t approximate_nns_nodes_visited() const noexcept { return _approximate_nns_nodes_visited; }
    void add_to_approximate_nns_nodes_visited(size_t value) noexcept { _approximate_nns_nodes_visited += value; }
};

/**
 * Class for collecting statistics from query evaluation.
 * Its methods are thread-safe so that search iterators from different threads
 * can write their collected statistics here.
 **/
class QueryEvalStats : public std::enable_shared_from_this<QueryEvalStats> {
private:
    struct Private {
        explicit Private() = default;
    };
    std::atomic<size_t> _exact_nns_distances_computed;

public:
    // Constructor is only usable by this class
    QueryEvalStats(Private) noexcept : _exact_nns_distances_computed(0) {}
    // This factory function has to be used to create objects, meaning that all such objects will be in a shared_ptr
    static std::shared_ptr<QueryEvalStats> create() { return std::make_shared<QueryEvalStats>(Private()); }

    size_t exact_nns_distances_computed() const noexcept {
        return _exact_nns_distances_computed.load(std::memory_order_relaxed);
    }
    void add_to_exact_nns_distances_computed(size_t value) noexcept {
        _exact_nns_distances_computed.fetch_add(value, std::memory_order_relaxed);
    }
};

} // namespace search::queryeval
