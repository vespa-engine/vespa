// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_metrics.h"
#include "disk_mem_usage_state.h"
#include <algorithm>

namespace proton {

DiskMemUsageMetrics::DiskMemUsageMetrics() noexcept
    : DiskMemUsageMetrics(DiskMemUsageState())
{
}

DiskMemUsageMetrics::DiskMemUsageMetrics(const DiskMemUsageState& usage) noexcept
    : _total_disk_usage(usage.diskState().usage()),
      _total_disk_utilization(usage.diskState().utilization()),
      _transient_disk_usage(usage.transient_disk_usage()),
      _non_transient_disk_usage(usage.non_transient_disk_usage()),
      _total_memory_usage(usage.memoryState().usage()),
      _total_memory_utilization(usage.memoryState().utilization()),
      _transient_memory_usage(usage.transient_memory_usage()),
      _non_transient_memory_usage(usage.non_transient_memory_usage())
{
}

void
DiskMemUsageMetrics::merge(const DiskMemUsageState& usage) noexcept
{
    _total_disk_usage = std::max(_total_disk_usage, usage.diskState().usage());
    _total_disk_utilization = std::max(_total_disk_utilization, usage.diskState().utilization());
    _transient_disk_usage = std::max(_transient_disk_usage, usage.transient_disk_usage());
    _non_transient_disk_usage = std::max(_non_transient_disk_usage, usage.non_transient_disk_usage());
    _total_memory_usage = std::max(_total_memory_usage, usage.memoryState().usage());
    _total_memory_utilization = std::max(_total_memory_utilization, usage.memoryState().utilization());
    _transient_memory_usage = std::max(_transient_memory_usage, usage.transient_memory_usage());
    _non_transient_memory_usage = std::max(_non_transient_memory_usage, usage.non_transient_memory_usage());
}

}
