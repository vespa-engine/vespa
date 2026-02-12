// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_sampler.h"
#include "resource_usage_write_filter.h"
#include <vespa/searchcore/proton/common/i_scheduled_executor.h>
#include <vespa/searchcore/proton/common/i_reserved_disk_space_provider.h>
#include <vespa/searchcorespi/common/i_resource_usage_provider.h>
#include <vespa/searchlib/util/directory_traverse.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <filesystem>

using search::DirectoryTraverse;
using searchcorespi::common::IResourceUsageProvider;
using searchcorespi::common::ResourceUsage;

using vespalib::makeLambdaTask;

namespace proton {

DiskMemUsageSampler::DiskMemUsageSampler(const std::string &path_in, ResourceUsageWriteFilter& filter,
                                         ResourceUsageNotifier& resource_usage_notifier,
                                         const IReservedDiskSpaceProvider& reserved_disk_space_provider)
    : _filter(filter),
      _notifier(resource_usage_notifier),
      _reserved_disk_space_provider(reserved_disk_space_provider),
      _path(path_in),
      _sampleInterval(60s),
      _lastSampleTime(),
      _lock(),
      _resource_usage_providers(),
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
    bool wasChanged = _notifier.setConfig(config.filterConfig);
    if (_periodicHandle && (_sampleInterval == config.sampleInterval) && !wasChanged) {
        return;
    }
    restart(config.sampleInterval, executor);
}

void
DiskMemUsageSampler::restart(std::optional<vespalib::duration> sample_interval, IScheduledExecutor& executor)
{
    _periodicHandle.reset();
    if (sample_interval.has_value()) {
        _sampleInterval = sample_interval.value();
    }
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
    ResourceUsage resource_usage = sample_resource_usage();
    /* It is important that transient resource usage is sampled first. This prevents
     * a false positive where we report a too high disk or memory usage causing
     * either feed blocked, or an alert due to metric spike.
     * A false negative is less of a problem, as it will only be a short drop in the metric,
     * and a short period of allowed feed. The latter will be very rare as you are rarely feed blocked anyway.
     */
    vespalib::ProcessMemoryStats memoryStats = sampleMemoryUsage();
    uint64_t diskUsage = sampleDiskUsage();
    uint64_t reserved_disk_space = _reserved_disk_space_provider.get_reserved_disk_space();
    _notifier.set_resource_usage(resource_usage, memoryStats, diskUsage, reserved_disk_space);
    _lastSampleTime = vespalib::steady_clock::now();
}

namespace {

namespace fs = std::filesystem;

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

}

uint64_t
DiskMemUsageSampler::sampleDiskUsage()
{
    const auto &disk = _notifier.getHwInfo().disk();
    return disk.shared()
        ? DirectoryTraverse::get_tree_size(_path)
        : sampleDiskUsageOnFileSystem(_path, disk);
}

vespalib::ProcessMemoryStats
DiskMemUsageSampler::sampleMemoryUsage()
{
    return vespalib::ProcessMemoryStats::create(0.01);
}

ResourceUsage
DiskMemUsageSampler::sample_resource_usage()
{
    ResourceUsage resource_usage;
    {
        std::lock_guard<std::mutex> guard(_lock);
        for (auto provider : _resource_usage_providers) {
            resource_usage.merge(provider->get_resource_usage());
        }
    }
    return resource_usage;
}

void
DiskMemUsageSampler::add_resource_usage_provider(std::shared_ptr<const IResourceUsageProvider> provider)
{
    std::lock_guard<std::mutex> guard(_lock);
    if (provider) {
        _resource_usage_providers.push_back(provider);
    }
}

void
DiskMemUsageSampler::remove_resource_usage_provider(std::shared_ptr<const IResourceUsageProvider> provider)
{
    std::lock_guard<std::mutex> guard(_lock);
    for (auto itr = _resource_usage_providers.begin(); itr != _resource_usage_providers.end(); ++itr) {
        if (*itr == provider) {
            _resource_usage_providers.erase(itr);
            break;
        }
    }
}

} // namespace proton
