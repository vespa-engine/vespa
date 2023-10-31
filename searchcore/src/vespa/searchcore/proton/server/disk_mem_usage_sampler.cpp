// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_sampler.h"
#include <vespa/searchcore/proton/common/i_scheduled_executor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/searchcore/proton/common/i_transient_resource_usage_provider.h>
#include <filesystem>

using vespalib::makeLambdaTask;

namespace proton {

DiskMemUsageSampler::DiskMemUsageSampler(const std::string &path_in, const vespalib::HwInfo &hwInfo)
    : _filter(hwInfo),
      _path(path_in),
      _sampleInterval(60s),
      _lastSampleTime(),
      _lock(),
      _transient_usage_providers(),
      _periodicHandle()
{
}

DiskMemUsageSampler::~DiskMemUsageSampler() = default;

void
DiskMemUsageSampler::close() {
    _periodicHandle.reset();
}

bool
DiskMemUsageSampler::timeToSampleAgain() const noexcept {
    return vespalib::steady_clock::now() >= (_lastSampleTime + _sampleInterval);
}

void
DiskMemUsageSampler::setConfig(const Config &config, IScheduledExecutor & executor)
{
    bool wasChanged = _filter.setConfig(config.filterConfig);
    if (_periodicHandle && (_sampleInterval == config.sampleInterval) && !wasChanged) {
        return;
    }
    _periodicHandle.reset();
    _sampleInterval = config.sampleInterval;
    sampleAndReportUsage();
    vespalib::duration maxInterval = std::min(vespalib::duration(1s), _sampleInterval);
    _periodicHandle = executor.scheduleAtFixedRate(makeLambdaTask([this]() {
        if (!_filter.acceptWriteOperation() || timeToSampleAgain()) {
            sampleAndReportUsage();
        }
    }), maxInterval, maxInterval);
}

void
DiskMemUsageSampler::sampleAndReportUsage()
{
    TransientResourceUsage transientUsage = sample_transient_resource_usage();
    /* It is important that transient resource usage is sampled first. This prevents
     * a false positive where we report a too high disk or memory usage causing
     * either feed blocked, or an alert due to metric spike.
     * A false negative is less of a problem, as it will only be a short drop in the metric,
     * and a short period of allowed feed. The latter will be very rare as you are rarely feed blocked anyway.
     */
    vespalib::ProcessMemoryStats memoryStats = sampleMemoryUsage();
    uint64_t diskUsage = sampleDiskUsage();
    _filter.set_resource_usage(transientUsage, memoryStats, diskUsage);
    _lastSampleTime = vespalib::steady_clock::now();
}

namespace {

namespace fs = std::filesystem;

// Disk usage for symbolic links and directories
constexpr uint64_t symlink_disk_usage = 4_Ki;
constexpr uint64_t directory_disk_usage = 4_Ki;

uint64_t
sampleDiskUsageOnFileSystem(const fs::path &path, const vespalib::HwInfo::Disk &disk)
{
    auto space_info = fs::space(path);
    uint64_t result = (space_info.capacity - space_info.available);
    if (result > disk.sizeBytes()) {
        return disk.sizeBytes();
    }
    return result;
}

// May throw fs::filesystem_error on concurrent directory tree modification
uint64_t
attemptSampleDirectoryDiskUsageOnce(const fs::path &path)
{
    uint64_t result = directory_disk_usage;
    for (const auto &elem : fs::recursive_directory_iterator(path, fs::directory_options::skip_permission_denied)) {
        if (elem.is_symlink()) {
            result += symlink_disk_usage;
        } else if (elem.is_regular_file()) {
            std::error_code fsize_err;
            const auto size = elem.file_size(fsize_err);
            // Errors here typically happens when a file is removed while doing the directory scan. Ignore them.
            if (!fsize_err) {
                result += size;
            }
        } else if (elem.is_directory()) {
            result += directory_disk_usage;
        }
    }
    return result;
}

uint64_t
sampleDiskUsageInDirectory(const fs::path &path)
{
    // Since attemptSampleDirectoryDiskUsageOnce may throw on concurrent directory
    // modifications, immediately retry a bounded number of times if this happens.
    // Number of retries chosen randomly by counting fingers.
    for (int i = 0; i < 10; ++i) {
        try {
            return attemptSampleDirectoryDiskUsageOnce(path);
        } catch (const fs::filesystem_error&) {
            // Go around for another spin that hopefully won't race.
        }
    }
    return 0;
}

}

uint64_t
DiskMemUsageSampler::sampleDiskUsage()
{
    const auto &disk = _filter.getHwInfo().disk();
    return disk.shared()
        ? sampleDiskUsageInDirectory(_path)
        : sampleDiskUsageOnFileSystem(_path, disk);
}

vespalib::ProcessMemoryStats
DiskMemUsageSampler::sampleMemoryUsage()
{
    return vespalib::ProcessMemoryStats::create();
}

TransientResourceUsage
DiskMemUsageSampler::sample_transient_resource_usage()
{
    TransientResourceUsage transient_usage;
    {
        std::lock_guard<std::mutex> guard(_lock);
        for (auto provider : _transient_usage_providers) {
            transient_usage.merge(provider->get_transient_resource_usage());
        }
    }
    return transient_usage;
}

void
DiskMemUsageSampler::add_transient_usage_provider(std::shared_ptr<const ITransientResourceUsageProvider> provider)
{
    std::lock_guard<std::mutex> guard(_lock);
    _transient_usage_providers.push_back(provider);
}

void
DiskMemUsageSampler::remove_transient_usage_provider(std::shared_ptr<const ITransientResourceUsageProvider> provider)
{
    std::lock_guard<std::mutex> guard(_lock);
    for (auto itr = _transient_usage_providers.begin(); itr != _transient_usage_providers.end(); ++itr) {
        if (*itr == provider) {
            _transient_usage_providers.erase(itr);
            break;
        }
    }
}

} // namespace proton
