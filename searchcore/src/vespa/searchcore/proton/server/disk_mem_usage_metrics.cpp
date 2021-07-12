// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_metrics.h"
#include "disk_mem_usage_state.h"
#include <algorithm>

namespace proton {

DiskMemUsageMetrics::DiskMemUsageMetrics() noexcept
    : DiskMemUsageMetrics(DiskMemUsageState())
{
}

DiskMemUsageMetrics::DiskMemUsageMetrics(const DiskMemUsageState &usage_state) noexcept
    : _disk_usage(usage_state.diskState().usage()),
      _disk_utilization(usage_state.diskState().utilization()),
      _memory_usage(usage_state.memoryState().usage()),
      _memory_utilization(usage_state.memoryState().utilization())
{
}

void
DiskMemUsageMetrics::merge(const DiskMemUsageState &usage_state) noexcept
{
    _disk_usage = std::max(_disk_usage, usage_state.diskState().usage());
    _disk_utilization = std::max(_disk_utilization, usage_state.diskState().utilization());
    _memory_usage = std::max(_memory_usage, usage_state.memoryState().usage());
    _memory_utilization = std::max(_memory_utilization, usage_state.memoryState().utilization());
}

}
