// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_sampler.h"
#include <vespa/vespalib/util/timer.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <experimental/filesystem>
#include <unistd.h>

using vespalib::makeLambdaTask;

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

namespace fs = std::experimental::filesystem;

uint64_t
sampleDiskUsageOnFileSystem(const fs::path &path, const HwInfo::Disk &disk)
{
    auto space_info = fs::space(path);
    uint64_t result = (space_info.capacity - space_info.available);
    if (result > disk.sizeBytes()) {
        return disk.sizeBytes();
    }
    return result;
}

uint64_t
sampleDiskUsageInDirectory(const fs::path &path)
{
    uint64_t result = 0;
    for (const auto &elem : fs::recursive_directory_iterator(path,
                                                             fs::directory_options::skip_permission_denied)) {
        if (fs::is_regular_file(elem.path()) && !fs::is_symlink(elem.path())) {
            try {
                result += fs::file_size(elem.path());
            } catch (const fs::filesystem_error &) {
                // This typically happens when a file is removed while doing the directory scan. Ignoring.
            }
        }
    }
    return result;
}

}

void
DiskMemUsageSampler::sampleDiskUsage()
{
    const auto &disk = _filter.getHwInfo().disk();
    _filter.setDiskUsedSize(disk.shared() ?
                            sampleDiskUsageInDirectory(_path) :
                            sampleDiskUsageOnFileSystem(_path, disk));
}

void
DiskMemUsageSampler::sampleMemoryUsage()
{
    _filter.setMemoryStats(vespalib::ProcessMemoryStats::create());
}

} // namespace proton
