// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_notifier.h"
#include "resource_usage_write_filter.h"
#include "i_resource_usage_listener.h"
#include <vespa/vespalib/util/hw_info.h>

namespace proton {

ResourceUsageNotifier::ResourceUsageNotifier(ResourceUsageWriteFilter& filter)
    : _lock(),
      _hwInfo(filter.get_hw_info()),
      _memoryStats(),
      _diskUsedSizeBytes(),
      _transient_usage(),
      _attribute_usage(),
      _config(),
      _usage_state(),
      _disk_mem_usage_metrics(),
      _listeners(),
      _filter(filter)
{ }

ResourceUsageNotifier::~ResourceUsageNotifier() = default;

void
ResourceUsageNotifier::recalcState(const Guard &guard, bool disk_mem_sample)
{
    double memoryUsed = getMemoryUsedRatio(guard);
    double diskUsed = getDiskUsedRatio(guard);
    ResourceUsageState usage(ResourceUsageWithLimit(diskUsed, _config._diskLimit),
                             ResourceUsageWithLimit(memoryUsed, _config._memoryLimit),
                             get_relative_transient_disk_usage(guard),
                             get_relative_transient_memory_usage(guard),
                             _attribute_usage);
    notify_resource_usage(guard, usage, disk_mem_sample);
}

double
ResourceUsageNotifier::getMemoryUsedRatio(const Guard&) const
{
    uint64_t unscaledMemoryUsed = _memoryStats.getAnonymousRss();
    return static_cast<double>(unscaledMemoryUsed) / _hwInfo.memory().sizeBytes();
}

double
ResourceUsageNotifier::getDiskUsedRatio(const Guard&) const
{
    double usedDiskSpaceRatio = static_cast<double>(_diskUsedSizeBytes) /
                                static_cast<double>(_hwInfo.disk().sizeBytes());
    return usedDiskSpaceRatio;
}

double
ResourceUsageNotifier::get_relative_transient_memory_usage(const Guard&) const
{
    return  static_cast<double>(_transient_usage.memory()) / _hwInfo.memory().sizeBytes();
}

double
ResourceUsageNotifier::get_relative_transient_disk_usage(const Guard&) const
{
    return  static_cast<double>(_transient_usage.disk()) / _hwInfo.disk().sizeBytes();
}

void
ResourceUsageNotifier::set_resource_usage(const TransientResourceUsage& transient_usage, vespalib::ProcessMemoryStats memoryStats, uint64_t diskUsedSizeBytes) {
    Guard guard(_lock);
    _transient_usage = transient_usage;
    _memoryStats = memoryStats;
    _diskUsedSizeBytes = diskUsedSizeBytes;
    recalcState(guard, true);
}

void
ResourceUsageNotifier::notify_attribute_usage(const AttributeUsageStats& attribute_usage)
{
    Guard guard(_lock);
    _attribute_usage = attribute_usage;
    recalcState(guard, false);
}

bool
ResourceUsageNotifier::setConfig(Config config_in)
{
    Guard guard(_lock);
    if (_config == config_in) return false;
    _config = config_in;
    recalcState(guard, false);
    return true;
}

vespalib::ProcessMemoryStats
ResourceUsageNotifier::getMemoryStats() const
{
    Guard guard(_lock);
    return _memoryStats;
}

uint64_t
ResourceUsageNotifier::getDiskUsedSize() const
{
    Guard guard(_lock);
    return _diskUsedSizeBytes;
}

TransientResourceUsage
ResourceUsageNotifier::get_transient_resource_usage() const
{
    Guard guard(_lock);
    return _transient_usage;
}

ResourceUsageNotifier::Config
ResourceUsageNotifier::getConfig() const
{
    Guard guard(_lock);
    return _config;
}

ResourceUsageState
ResourceUsageNotifier::usageState() const
{
    Guard guard(_lock);
    return _usage_state;
}

DiskMemUsageMetrics
ResourceUsageNotifier::get_metrics() const
{
    Guard guard(_lock);
    DiskMemUsageMetrics result(_disk_mem_usage_metrics);
    _disk_mem_usage_metrics = DiskMemUsageMetrics(_usage_state);
    return result;
}

void
ResourceUsageNotifier::add_resource_usage_listener(IResourceUsageListener *listener)
{
    Guard guard(_lock);
    _listeners.push_back(listener);
    listener->notify_resource_usage(_usage_state);
}

void
ResourceUsageNotifier::remove_resource_usage_listener(IResourceUsageListener *listener)
{
    Guard guard(_lock);
    for (auto itr = _listeners.begin(); itr != _listeners.end(); ++itr) {
        if (*itr == listener) {
            _listeners.erase(itr);
            break;
        }
    }
}

void
ResourceUsageNotifier::notify_resource_usage(const Guard &guard, ResourceUsageState state, bool disk_mem_sample)
{
    (void) guard;
    _usage_state = state;
    if (disk_mem_sample) {
        _disk_mem_usage_metrics.merge(state);
    }
    _filter.notify_resource_usage(_usage_state, _memoryStats, _diskUsedSizeBytes);
    for (const auto &listener : _listeners) {
        listener->notify_resource_usage(_usage_state);
    }
}

} // namespace proton
