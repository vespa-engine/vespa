// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

class DiskMemUsageState;

/**
 * Class containing disk and memory usage in a form suitable for
 * metrics reporting.
 */
class DiskMemUsageMetrics
{
    double _disk_usage;
    double _disk_utilization;
    double _memory_usage;
    double _memory_utilization;

public:
    DiskMemUsageMetrics() noexcept;
    DiskMemUsageMetrics(const DiskMemUsageState &usage_state) noexcept;
    void merge(const DiskMemUsageState &usage_state) noexcept;
    double get_disk_usage() const noexcept { return _disk_usage; }
    double get_disk_utilization() const noexcept { return _disk_utilization; }
    double get_memory_usage() const noexcept { return _memory_usage; }
    double get_memory_utilization() const noexcept { return _memory_utilization; }
};

} // namespace proton
