// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <boost/filesystem.hpp>
#include <vespa/vespalib/util/process_memory_stats.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <mutex>
#include <atomic>

namespace proton {

/*
 * Class to filter write operations based on sampled disk and memory
 * usage. If resource limit is reached then further writes are denied
 * in order to prevent entering an unrecoverable state.
 */
class DiskMemUsageFilter : public IResourceWriteFilter {
public:
    using space_info = boost::filesystem::space_info;
    using Mutex = std::mutex;
    using Guard = std::lock_guard<Mutex>;

    struct Config
    {
        double _memoryLimit;
        double _diskLimit;

        Config()
            : _memoryLimit(1.0),
              _diskLimit(1.0)
        {
        }

        Config(double memoryLimit_in, double diskLimit_in)
            : _memoryLimit(memoryLimit_in),
              _diskLimit(diskLimit_in)
        {
        }
    };

private:
    mutable Mutex _lock; // protect _memoryStats, _diskStats, _config, _state
    vespalib::ProcessMemoryStats _memoryStats;
    uint64_t _physicalMemory;
    space_info _diskStats;
    Config _config;
    State _state;
    std::atomic<bool> _acceptWrite;

    void recalcState(const Guard &guard); // called with _lock held
    double getMemoryUsedRatio(const Guard &guard) const;
    double getDiskUsedRatio(const Guard &guard) const;

public:
    DiskMemUsageFilter(uint64_t physicalMememory_in);
    void setMemoryStats(vespalib::ProcessMemoryStats memoryStats_in);
    void setDiskStats(space_info diskStats_in);
    void setConfig(Config config);
    vespalib::ProcessMemoryStats getMemoryStats() const;
    space_info getDiskStats() const;
    Config getConfig() const;
    uint64_t getPhysicalMemory() const { return _physicalMemory; }
    double getMemoryUsedRatio() const;
    double getDiskUsedRatio() const;
    virtual bool acceptWriteOperation() const override;
    virtual State getAcceptState() const override;
};


} // namespace proton
