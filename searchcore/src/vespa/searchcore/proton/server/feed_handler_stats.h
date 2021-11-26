// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <optional>

namespace proton {

/*
 * Stats for feed handler.
 */
class FeedHandlerStats
{
    uint64_t                _commits;
    uint64_t                _operations;
    double                  _total_latency;
    std::optional<uint32_t> _min_operations;
    std::optional<uint32_t> _max_operations;
    std::optional<double>   _min_latency;
    std::optional<double>   _max_latency;

public:
    FeedHandlerStats(uint64_t commits, uint64_t operations, double total_latency) noexcept;
    FeedHandlerStats() noexcept;
    ~FeedHandlerStats();
    FeedHandlerStats& operator-=(const FeedHandlerStats& rhs) noexcept;
    void add_commit(uint32_t operations, double latency) noexcept;
    void reset_min_max() noexcept;
    uint64_t get_commits() noexcept { return _commits; }
    uint64_t get_operations() noexcept { return _operations; }
    double get_total_latency() noexcept { return _total_latency; }
    const std::optional<uint32_t>& get_min_operations() noexcept { return _min_operations; }
    const std::optional<uint32_t>& get_max_operations() noexcept { return _max_operations; }
    const std::optional<double>& get_min_latency() noexcept { return _min_latency; }
    const std::optional<double>& get_max_latency() noexcept { return _max_latency; }
};

}
