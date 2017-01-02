// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "disk_mem_usage_filter.h"

namespace vespalib { class Timer; }

namespace proton {

/*
 * Class to sample disk and memory usage used for filtering write operations.
 */
class DiskMemUsageSampler {
    DiskMemUsageFilter _filter;
    std::experimental::filesystem::path _path;
    double _sampleInterval;
    std::unique_ptr<vespalib::Timer> _periodicTimer;

    void sampleUsage();
    void sampleDiskUsage();
    void sampleMemoryUsage();
public:
    struct Config {
        DiskMemUsageFilter::Config _filterConfig;
        double _sampleInterval;
    public:
        Config()
            : _filterConfig(),
              _sampleInterval(60.0)
        {
        }

        Config(double memoryLimit_in, double diskLimit_in,
               double sampleInterval_in)
            : _filterConfig(memoryLimit_in, diskLimit_in),
              _sampleInterval(sampleInterval_in)
        {
        }
    };

    DiskMemUsageSampler(const std::string &path_in,
                        const Config &config);

    ~DiskMemUsageSampler();

    void setConfig(const Config &config);

    const DiskMemUsageFilter &writeFilter() const { return _filter; }
    IDiskMemUsageNotifier &notifier() { return _filter; }
};


} // namespace proton
