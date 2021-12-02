// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "active_operations_stats.h"

namespace storage {

namespace {

template <typename T>
void update_min_max(T value, std::optional<T>& min, std::optional<T>& max)
{
    if (!min.has_value() || value < min.value()) {
        min = value;
    }
    if (!max.has_value() || value > max.value()) {
        max = value;
    }
}

template <typename T>
void merge_min(std::optional<T>& min, const std::optional<T>& rhs_min)
{
    if (!rhs_min.has_value()) {
        return;
    }
    if (min.has_value() && !(rhs_min.value() < min.value())) {
        return;
    }
    min = rhs_min;
}

template <typename T>
void merge_max(std::optional<T>& max, const std::optional<T>& rhs_max)
{
    if (!rhs_max.has_value()) {
        return;
    }
    if (max.has_value() && !(rhs_max.value() > max.value())) {
        return;
    }
    max = rhs_max;
}

template <typename T>
void merge_min_max_sum(std::optional<T>& lhs, const std::optional<T>& rhs)
{
    if (!rhs.has_value()) {
        return;
    }
    if (lhs.has_value()) {
        lhs = lhs.value() + rhs.value();
        return;
    }
    lhs = rhs;
}

}

ActiveOperationsStats::ActiveOperationsStats() noexcept
    : _size_samples(0u),
      _total_size(0u),
      _active_size(0u),
      _min_size(),
      _max_size(),
      _latency_samples(0u),
      _total_latency(0.0),
      _min_latency(),
      _max_latency()
{
}

ActiveOperationsStats::~ActiveOperationsStats() = default;


void
ActiveOperationsStats::update_size() noexcept
{
    ++_size_samples;
    _total_size += _active_size;
    update_min_max(_active_size, _min_size, _max_size);
}

ActiveOperationsStats&
ActiveOperationsStats::operator-=(const ActiveOperationsStats& rhs) noexcept
{
    _size_samples -= rhs._size_samples;
    _total_size -= rhs._total_size;
    _latency_samples -= rhs._latency_samples;
    _total_latency -= rhs._total_latency;
    return *this;
}

void
ActiveOperationsStats::merge(const ActiveOperationsStats& rhs) noexcept
{
    _size_samples += rhs._size_samples;
    _total_size += rhs._total_size;
    _active_size += rhs._active_size;
    merge_min_max_sum(_min_size, rhs._min_size);
    merge_min_max_sum(_max_size, rhs._max_size);
    _latency_samples += rhs._latency_samples;
    _total_latency += rhs._total_latency;
    merge_min(_min_latency, rhs._min_latency);
    merge_max(_max_latency, rhs._max_latency);
}

void
ActiveOperationsStats::operation_started() noexcept
{
    ++_active_size;
    update_size();
}

void
ActiveOperationsStats::operation_done(double latency) noexcept
{
    --_active_size;
    update_size();
    ++_latency_samples;
    _total_latency += latency;
    update_min_max(latency, _min_latency, _max_latency);
}

void
ActiveOperationsStats::reset_min_max() noexcept
{
    _min_size.reset();
    _max_size.reset();
    _min_latency.reset();
    _max_latency.reset();
}

}
