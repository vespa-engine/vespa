// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_disk_mem_usage_notifier.h"
#include "disk_mem_usage_state.h"
#include "disk_mem_usage_metrics.h"
#include <vespa/searchcore/proton/common/i_transient_resource_usage_provider.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/process_memory_stats.h>
#include <atomic>
#include <filesystem>
#include <mutex>
#include <vector>

namespace proton {

class ResourceUsageWriteFilter;

/**
 * Class to notify disk and memory usage based on sampled disk and memory usage.
 * The notification includes the configured limits.
 */
class DiskMemUsageNotifier : public IDiskMemUsageNotifier {
public:
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

    struct Config
    {
        double _memoryLimit;
        double _diskLimit;

        Config() : Config(1.0, 1.0) { }

        Config(double memoryLimit_in, double diskLimit_in)
            : _memoryLimit(memoryLimit_in),
              _diskLimit(diskLimit_in)
        { }
        bool operator == (const Config & rhs) const noexcept {
            return (_memoryLimit == rhs._memoryLimit) && (_diskLimit == rhs._diskLimit);
        }
        bool operator != (const Config & rhs) const noexcept {
            return ! (*this == rhs);
        }
    };

private:
    mutable Mutex                _lock;
    vespalib::HwInfo             _hwInfo;
    // Following member variables are protected by _lock
    vespalib::ProcessMemoryStats _memoryStats;
    uint64_t                     _diskUsedSizeBytes;
    TransientResourceUsage       _transient_usage;
    Config                       _config;
    DiskMemUsageState            _dmstate;
    mutable DiskMemUsageMetrics  _disk_mem_usage_metrics;
    std::vector<IDiskMemUsageListener *> _listeners;
    ResourceUsageWriteFilter&    _filter;

    void recalcState(const Guard &guard); // called with _lock held
    double getMemoryUsedRatio(const Guard &guard) const;
    double getDiskUsedRatio(const Guard &guard) const;
    double get_relative_transient_memory_usage(const Guard& guard) const;
    double get_relative_transient_disk_usage(const Guard& guard) const;
    void notifyDiskMemUsage(const Guard &guard, DiskMemUsageState state);

public:
    DiskMemUsageNotifier(ResourceUsageWriteFilter& filter);
    ~DiskMemUsageNotifier() override;
    void set_resource_usage(const TransientResourceUsage& transient_usage, vespalib::ProcessMemoryStats memoryStats, uint64_t diskUsedSizeBytes);
    [[nodiscard]] bool setConfig(Config config);
    vespalib::ProcessMemoryStats getMemoryStats() const;
    uint64_t getDiskUsedSize() const;
    TransientResourceUsage get_transient_resource_usage() const;
    Config getConfig() const;
    const vespalib::HwInfo &getHwInfo() const noexcept { return _hwInfo; }
    DiskMemUsageState usageState() const;
    DiskMemUsageMetrics get_metrics() const;
    void addDiskMemUsageListener(IDiskMemUsageListener *listener) override;
    void removeDiskMemUsageListener(IDiskMemUsageListener *listener) override;
};


} // namespace proton
