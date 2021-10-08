// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_disk_mem_usage_notifier.h"
#include "disk_mem_usage_state.h"
#include "disk_mem_usage_metrics.h"
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/vespalib/util/process_memory_stats.h>
#include <atomic>
#include <filesystem>
#include <mutex>
#include <vector>

namespace proton {

/*
 * Class to filter write operations based on sampled disk and memory
 * usage. If resource limit is reached then further writes are denied
 * in order to prevent entering an unrecoverable state.
 */
class DiskMemUsageFilter : public IResourceWriteFilter,
                           public IDiskMemUsageNotifier {
public:
    using space_info = std::filesystem::space_info;
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
    };

private:
    mutable Mutex                _lock;
    HwInfo                       _hwInfo;
    std::atomic<bool>            _acceptWrite;
    // Following member variables are protected by _lock
    vespalib::ProcessMemoryStats _memoryStats;
    uint64_t                     _diskUsedSizeBytes;
    size_t                       _transient_memory_usage;
    size_t                       _transient_disk_usage;
    Config                       _config;
    State                        _state;
    DiskMemUsageState            _dmstate;
    mutable DiskMemUsageMetrics  _disk_mem_usage_metrics;
    std::vector<IDiskMemUsageListener *> _listeners;

    void recalcState(const Guard &guard); // called with _lock held
    double getMemoryUsedRatio(const Guard &guard) const;
    double getDiskUsedRatio(const Guard &guard) const;
    void notifyDiskMemUsage(const Guard &guard, DiskMemUsageState state);

public:
    DiskMemUsageFilter(const HwInfo &hwInfo);
    ~DiskMemUsageFilter() override;
    void setMemoryStats(vespalib::ProcessMemoryStats memoryStats_in);
    void setDiskUsedSize(uint64_t diskUsedSizeBytes);
    void set_transient_resource_usage(size_t transient_memory_usage, size_t transient_disk_usage);
    void setConfig(Config config);
    vespalib::ProcessMemoryStats getMemoryStats() const;
    uint64_t getDiskUsedSize() const;
    size_t get_transient_memory_usage() const;
    double get_relative_transient_memory_usage() const;
    size_t get_transient_disk_usage() const;
    double get_relative_transient_disk_usage() const;
    Config getConfig() const;
    const HwInfo &getHwInfo() const { return _hwInfo; }
    DiskMemUsageState usageState() const;
    DiskMemUsageMetrics get_metrics() const;
    bool acceptWriteOperation() const override;
    State getAcceptState() const override;
    void addDiskMemUsageListener(IDiskMemUsageListener *listener) override;
    void removeDiskMemUsageListener(IDiskMemUsageListener *listener) override;
};


} // namespace proton
