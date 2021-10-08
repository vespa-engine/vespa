// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/util/time.h>
#include "disk_mem_usage_filter.h"

namespace vespalib { class ScheduledExecutor; }

namespace proton {

class ITransientResourceUsageProvider;

/*
 * Class to sample disk and memory usage used for filtering write operations.
 */
class DiskMemUsageSampler {
    DiskMemUsageFilter    _filter;
    std::filesystem::path _path;
    vespalib::duration    _sampleInterval;
    vespalib::steady_time _lastSampleTime;
    std::unique_ptr<vespalib::ScheduledExecutor> _periodicTimer;
    std::mutex            _lock;
    std::vector<std::shared_ptr<const ITransientResourceUsageProvider>> _transient_usage_providers;

    void sampleUsage();
    void sampleDiskUsage();
    void sampleMemoryUsage();
    void sample_transient_resource_usage();
public:
    struct Config {
        DiskMemUsageFilter::Config filterConfig;
        vespalib::duration sampleInterval;
        HwInfo hwInfo;

        Config()
            : filterConfig(),
              sampleInterval(60s),
              hwInfo()
        {
        }

        Config(double memoryLimit_in,
               double diskLimit_in,
               vespalib::duration sampleInterval_in,
               const HwInfo &hwInfo_in)
            : filterConfig(memoryLimit_in, diskLimit_in),
              sampleInterval(sampleInterval_in),
              hwInfo(hwInfo_in)
        {
        }
    };

    DiskMemUsageSampler(const std::string &path_in,
                        const Config &config);

    ~DiskMemUsageSampler();

    void setConfig(const Config &config);

    const DiskMemUsageFilter &writeFilter() const { return _filter; }
    IDiskMemUsageNotifier &notifier() { return _filter; }
    void add_transient_usage_provider(std::shared_ptr<const ITransientResourceUsageProvider> provider);
    void remove_transient_usage_provider(std::shared_ptr<const ITransientResourceUsageProvider> provider);
};


} // namespace proton
