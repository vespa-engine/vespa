// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_sampler.h"
#include <vespa/vespalib/util/timer.h>
#include <vespa/searchlib/common/lambdatask.h>
#include <cstdlib>

using search::makeLambdaTask;

namespace proton {

namespace {

uint64_t getPhysicalMemoryBytes()
{
    // TODO: Temporal workaround for Docker nodes. Remove when this is part of proton.cfg instead.
    if (const char *memoryEnv = std::getenv("VESPA_TOTAL_MEMORY_MB")) {
        uint64_t physicalMemoryMB = atoll(memoryEnv);
        return physicalMemoryMB * 1024u * 1024u;
    } else {
        return sysconf(_SC_PHYS_PAGES) * sysconf(_SC_PAGESIZE);
    }
}

} // namespace proton:<anonymous>

DiskMemUsageSampler::DiskMemUsageSampler(const std::string &path_in,
                                         const Config &config)
    : _filter(getPhysicalMemoryBytes()),
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
    _filter.setConfig(config._filterConfig);
    _sampleInterval = config._sampleInterval;
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

void
DiskMemUsageSampler::sampleDiskUsage()
{
    _filter.setDiskStats(std::experimental::filesystem::space(_path));
}

void
DiskMemUsageSampler::sampleMemoryUsage()
{
    _filter.setMemoryStats(vespalib::ProcessMemoryStats::create());
}

} // namespace proton
