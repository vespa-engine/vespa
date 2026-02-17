// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resource_usage_notifier.h"
#include <vespa/searchcore/proton/attribute/attribute_usage_filter_config.h>
#include <vespa/searchcore/proton/common/i_scheduled_executor.h>
#include <optional>

namespace searchcorespi::common {

class IResourceUsageProvider;
class ResourceUsage;

}

namespace vespalib { class IDestructorCallback; }

namespace proton {

class IReservedDiskSpaceProvider;

/*
 * Class to sample disk and memory usage used for filtering write operations.
 */
class DiskMemUsageSampler {
    ResourceUsageWriteFilter&         _filter;
    ResourceUsageNotifier&            _notifier;
    const IReservedDiskSpaceProvider& _reserved_disk_space_provider;
    std::filesystem::path             _path;
    vespalib::duration                _sampleInterval;
    vespalib::steady_time             _lastSampleTime;
    std::mutex                        _lock;
    std::vector<std::shared_ptr<const searchcorespi::common::IResourceUsageProvider>> _resource_usage_providers;
    std::unique_ptr<vespalib::IDestructorCallback> _periodicHandle;

    void sampleAndReportUsage();
    uint64_t sampleDiskUsage();
    vespalib::ProcessMemoryStats sampleMemoryUsage();
    searchcorespi::common::ResourceUsage sample_resource_usage();
    [[nodiscard]] bool timeToSampleAgain() const noexcept;
    void restart(std::optional<vespalib::duration> sample_interval, IScheduledExecutor& executor);
public:
    struct Config {
        ResourceUsageNotifier::Config filterConfig;
        vespalib::duration sampleInterval;
        vespalib::HwInfo hwInfo;

        Config()
            : filterConfig(),
              sampleInterval(60s),
              hwInfo()
        { }

        Config(double memoryLimit_in,
               double diskLimit_in,
               double reserved_disk_space_factor_in,
               AttributeUsageFilterConfig attribute_limit_in,
               vespalib::duration sampleInterval_in,
               const vespalib::HwInfo &hwInfo_in)
            : filterConfig(memoryLimit_in, diskLimit_in, reserved_disk_space_factor_in, attribute_limit_in),
              sampleInterval(sampleInterval_in),
              hwInfo(hwInfo_in)
        { }
    };

    DiskMemUsageSampler(const std::string &path_in, ResourceUsageWriteFilter& filter,
                        ResourceUsageNotifier& resource_usage_notifier,
                        const IReservedDiskSpaceProvider& reserved_disk_space_provider);
    ~DiskMemUsageSampler();
    void close();

    void setConfig(const Config &config, IScheduledExecutor & executor);
    void restart(IScheduledExecutor& executor) { restart(std::nullopt, executor); }

    void add_resource_usage_provider(std::shared_ptr<const searchcorespi::common::IResourceUsageProvider> provider);
    void remove_resource_usage_provider(std::shared_ptr<const searchcorespi::common::IResourceUsageProvider> provider);
};

} // namespace proton
