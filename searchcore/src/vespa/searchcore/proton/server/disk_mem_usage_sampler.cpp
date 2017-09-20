// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_sampler.h"
#include <vespa/vespalib/util/timer.h>
#include <vespa/searchlib/common/lambdatask.h>
#include <experimental/filesystem>
#include <unistd.h>

using search::makeLambdaTask;

namespace proton {

DiskMemUsageSampler::DiskMemUsageSampler(const std::string &path_in,
                                         const Config &config)
    : _filter(config.hwInfo),
      _path(path_in),
      _sampleInterval(60.0),
      _periodicTimer()
{
    setConfig(config);
}

DiskMemUsageSampler::~DiskMemUsageSampler()
{
    _periodicTimer.reset();
}

void
DiskMemUsageSampler::setConfig(const Config &config)
{
    _periodicTimer.reset();
    _filter.setConfig(config.filterConfig);
    _sampleInterval = config.sampleInterval;
    sampleUsage();
    _periodicTimer = std::make_unique<vespalib::Timer>();
    _periodicTimer->scheduleAtFixedRate(makeLambdaTask([this]()
                                                       { sampleUsage(); }),
                                        _sampleInterval, _sampleInterval);
}

void
DiskMemUsageSampler::sampleUsage()
{
    sampleMemoryUsage();
    sampleDiskUsage();
}

namespace {

uint64_t
sampleDiskUsageInDirectory(const std::experimental::filesystem::path &path)
{
    uint64_t result = 0;
    for (const auto &elem : std::experimental::filesystem::recursive_directory_iterator(path)) {
        if (!std::experimental::filesystem::is_directory(elem.path())) {
            result += std::experimental::filesystem::file_size(elem.path());
        }
    }
    return result;
}

}

void
DiskMemUsageSampler::sampleDiskUsage()
{
    _filter.setDiskStats(sampleDiskUsageInDirectory(_path));
}

void
DiskMemUsageSampler::sampleMemoryUsage()
{
    _filter.setMemoryStats(vespalib::ProcessMemoryStats::create());
}

} // namespace proton
