// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

class DiskMemUsageState;

/**
 * Class containing disk and memory usage in a form suitable for
 * metrics reporting.
 */
class DiskMemUsageMetrics
{
    double _total_disk_usage;
    double _total_disk_utilization;
    double _transient_disk_usage;
    double _non_transient_disk_usage;
    double _total_memory_usage;
    double _total_memory_utilization;
    double _transient_memory_usage;
    double _non_transient_memory_usage;

public:
    DiskMemUsageMetrics() noexcept;
    DiskMemUsageMetrics(const DiskMemUsageState& usage) noexcept;
    void merge(const DiskMemUsageState& usage) noexcept;
    double total_disk_usage() const noexcept { return _total_disk_usage; }
    double total_disk_utilization() const noexcept { return _total_disk_utilization; }
    double transient_disk_usage() const noexcept { return _transient_disk_usage; }
    double non_transient_disk_usage() const noexcept { return _non_transient_disk_usage; }
    double total_memory_usage() const noexcept { return _total_memory_usage; }
    double total_memory_utilization() const noexcept { return _total_memory_utilization; }
    double transient_memory_usage() const noexcept { return _transient_memory_usage; }
    double non_transient_memory_usage() const noexcept { return _non_transient_memory_usage; }
};

} // namespace proton
