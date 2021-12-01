// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <optional>

namespace storage {

/*
 * Stats for active operations at service layer
 */
class ActiveOperationsStats
{
    uint64_t                _size_samples;
    uint64_t                _total_size;
    uint32_t                _active_size;
    std::optional<uint32_t> _min_size;
    std::optional<uint32_t> _max_size;
    uint64_t                _latency_samples;
    double                  _total_latency;
    std::optional<double>   _min_latency;
    std::optional<double>   _max_latency;

    void update_size() noexcept;
public:
    ActiveOperationsStats() noexcept;
    ~ActiveOperationsStats();
    ActiveOperationsStats& operator-=(const ActiveOperationsStats& rhs) noexcept;
    void merge(const ActiveOperationsStats& rhs) noexcept;
    void operation_started() noexcept;
    void operation_done(double latency) noexcept;
    void reset_min_max() noexcept;
    uint64_t get_size_samples() const noexcept { return _size_samples; }
    uint64_t get_latency_samples() const noexcept { return _latency_samples; }
    uint64_t get_total_size() const noexcept { return _total_size; }
    uint32_t get_active_size() const noexcept { return _active_size; }
    double get_total_latency() const noexcept { return _total_latency; }
    const std::optional<uint32_t>& get_min_size() const noexcept { return _min_size; }
    const std::optional<uint32_t>& get_max_size() const noexcept { return _max_size; }
    const std::optional<double>& get_min_latency() const noexcept { return _min_latency; }
    const std::optional<double>& get_max_latency() const noexcept { return _max_latency; }
};

}
