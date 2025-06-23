// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resource_usage_notifier.h"
#include <vespa/searchcore/proton/attribute/attribute_usage_filter_config.h>
#include <vespa/searchcore/proton/common/i_scheduled_executor.h>

namespace vespalib { class IDestructorCallback; }

namespace proton {

class ITransientResourceUsageProvider;

/*
 * Class to sample disk and memory usage used for filtering write operations.
 */
class DiskMemUsageSampler {
    ResourceUsageWriteFilter& _filter;
    ResourceUsageNotifier&    _notifier;
    std::filesystem::path     _path;
    vespalib::duration        _sampleInterval;
    vespalib::steady_time     _lastSampleTime;
    std::mutex                _lock;
    std::vector<std::shared_ptr<const ITransientResourceUsageProvider>> _transient_usage_providers;
    std::unique_ptr<vespalib::IDestructorCallback> _periodicHandle;

    void sampleAndReportUsage();
    uint64_t sampleDiskUsage();
    vespalib::ProcessMemoryStats sampleMemoryUsage();
    TransientResourceUsage sample_transient_resource_usage();
    [[nodiscard]] bool timeToSampleAgain() const noexcept;
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
               AttributeUsageFilterConfig attribute_limit_in,
               vespalib::duration sampleInterval_in,
               const vespalib::HwInfo &hwInfo_in)
            : filterConfig(memoryLimit_in, diskLimit_in, attribute_limit_in),
              sampleInterval(sampleInterval_in),
              hwInfo(hwInfo_in)
        { }
    };

    DiskMemUsageSampler(const std::string &path_in, ResourceUsageWriteFilter& filter,
                        ResourceUsageNotifier& resource_usage_notifier);
    ~DiskMemUsageSampler();
    void close();

    void setConfig(const Config &config, IScheduledExecutor & executor);

    void add_transient_usage_provider(std::shared_ptr<const ITransientResourceUsageProvider> provider);
    void remove_transient_usage_provider(std::shared_ptr<const ITransientResourceUsageProvider> provider);
};

} // namespace proton
