// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resource_usage_notifier.h"

#include <vespa/searchcore/proton/attribute/attribute_usage_filter_config.h>
#include <vespa/searchcore/proton/common/i_scheduled_executor.h>

#include <optional>

namespace searchcorespi::common {

class IResourceUsageProvider;
class ResourceUsage;

} // namespace searchcorespi::common

namespace vespalib {
class IDestructorCallback;
}

namespace proton {

class IReservedDiskSpaceAndMemoryProvider;

/**
 * Disk usage sample.
 */
class DiskUsage {
    uint64_t _used_bytes;
    uint64_t _capacity_bytes;

public:
    DiskUsage(uint64_t used_bytes, uint64_t capacity_bytes) noexcept
        : _used_bytes(used_bytes), _capacity_bytes(capacity_bytes) {}
    [[nodiscard]] uint64_t used_bytes() const noexcept { return _used_bytes; }
    [[nodiscard]] uint64_t capacity_bytes() const noexcept { return _capacity_bytes; }
};

/*
 * Class to sample disk and memory usage used for filtering write operations.
 */
class DiskMemUsageSampler {
    ResourceUsageWriteFilter&                  _filter;
    ResourceUsageNotifier&                     _notifier;
    const IReservedDiskSpaceAndMemoryProvider& _reserved_disk_space_and_memory_provider;
    std::filesystem::path                      _path;
    bool                                       _should_resample_disk_capacity;
    vespalib::duration                         _sampleInterval;
    vespalib::steady_time                      _lastSampleTime;
    std::mutex                                 _lock;
    std::vector<std::shared_ptr<const searchcorespi::common::IResourceUsageProvider>> _resource_usage_providers;
    std::unique_ptr<vespalib::IDestructorCallback>                                    _periodicHandle;

    void sampleAndReportUsage();
    [[nodiscard]] DiskUsage sampleDiskUsage(const searchcorespi::common::ResourceUsage& resource_usage);
    vespalib::ProcessMemoryStats sampleMemoryUsage();
    searchcorespi::common::ResourceUsage sample_resource_usage();
    [[nodiscard]] bool timeToSampleAgain() const noexcept;
    void restart(std::optional<vespalib::duration> sample_interval, IScheduledExecutor& executor);

public:
    struct Config {
        ResourceUsageNotifier::Config filterConfig;
        vespalib::duration            sampleInterval;
        vespalib::HwInfo              hwInfo;
        bool                          should_resample_disk_capacity;

        Config() : filterConfig(), sampleInterval(60s), hwInfo(), should_resample_disk_capacity(false) {}

        Config(double memoryLimit_in, double diskLimit_in, double reserved_disk_space_factor_in,
               double reserved_memory_factor_in, AttributeUsageFilterConfig attribute_limit_in,
               vespalib::duration sampleInterval_in, const vespalib::HwInfo& hwInfo_in,
               bool should_resample_disk_capacity_in)
            : filterConfig(memoryLimit_in, diskLimit_in, reserved_disk_space_factor_in, reserved_memory_factor_in,
                           attribute_limit_in),
              sampleInterval(sampleInterval_in),
              hwInfo(hwInfo_in),
              should_resample_disk_capacity(should_resample_disk_capacity_in) {}
    };

    DiskMemUsageSampler(const std::string& path_in, ResourceUsageWriteFilter& filter,
                        ResourceUsageNotifier&                     resource_usage_notifier,
                        const IReservedDiskSpaceAndMemoryProvider& reserved_disk_space_and_memory_provider);
    ~DiskMemUsageSampler();
    void close();

    void setConfig(const Config& config, IScheduledExecutor& executor);
    void restart(IScheduledExecutor& executor) { restart(std::nullopt, executor); }

    void add_resource_usage_provider(std::shared_ptr<const searchcorespi::common::IResourceUsageProvider> provider);
    void
    remove_resource_usage_provider(std::shared_ptr<const searchcorespi::common::IResourceUsageProvider> provider);
};

} // namespace proton
