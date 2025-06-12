// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_notifier.h"
#include "resource_usage_write_filter.h"
#include "i_disk_mem_usage_listener.h"
#include <vespa/vespalib/util/hw_info.h>

namespace proton {

void
DiskMemUsageNotifier::recalcState(const Guard &guard)
{
    double memoryUsed = getMemoryUsedRatio(guard);
    double diskUsed = getDiskUsedRatio(guard);
    DiskMemUsageState dmstate(ResourceUsageState(_config._diskLimit, diskUsed),
                              ResourceUsageState(_config._memoryLimit, memoryUsed),
                              get_relative_transient_disk_usage(guard),
                              get_relative_transient_memory_usage(guard));
    notifyDiskMemUsage(guard, dmstate);
}

double
DiskMemUsageNotifier::getMemoryUsedRatio(const Guard&) const
{
    uint64_t unscaledMemoryUsed = _memoryStats.getAnonymousRss();
    return static_cast<double>(unscaledMemoryUsed) / _hwInfo.memory().sizeBytes();
}

double
DiskMemUsageNotifier::getDiskUsedRatio(const Guard&) const
{
    double usedDiskSpaceRatio = static_cast<double>(_diskUsedSizeBytes) /
                                static_cast<double>(_hwInfo.disk().sizeBytes());
    return usedDiskSpaceRatio;
}

double
DiskMemUsageNotifier::get_relative_transient_memory_usage(const Guard&) const
{
    return  static_cast<double>(_transient_usage.memory()) / _hwInfo.memory().sizeBytes();
}

double
DiskMemUsageNotifier::get_relative_transient_disk_usage(const Guard&) const
{
    return  static_cast<double>(_transient_usage.disk()) / _hwInfo.disk().sizeBytes();
}

DiskMemUsageNotifier::DiskMemUsageNotifier(ResourceUsageWriteFilter& filter)
    : _lock(),
      _hwInfo(filter.get_hw_info()),
      _memoryStats(),
      _diskUsedSizeBytes(),
      _transient_usage(),
      _config(),
      _dmstate(),
      _disk_mem_usage_metrics(),
      _listeners(),
      _filter(filter)
{ }

DiskMemUsageNotifier::~DiskMemUsageNotifier() = default;

void
DiskMemUsageNotifier::set_resource_usage(const TransientResourceUsage& transient_usage, vespalib::ProcessMemoryStats memoryStats, uint64_t diskUsedSizeBytes) {
    Guard guard(_lock);
    _transient_usage = transient_usage;
    _memoryStats = memoryStats;
    _diskUsedSizeBytes = diskUsedSizeBytes;
    recalcState(guard);
}

bool
DiskMemUsageNotifier::setConfig(Config config_in)
{
    Guard guard(_lock);
    if (_config == config_in) return false;
    _config = config_in;
    recalcState(guard);
    return true;
}

vespalib::ProcessMemoryStats
DiskMemUsageNotifier::getMemoryStats() const
{
    Guard guard(_lock);
    return _memoryStats;
}

uint64_t
DiskMemUsageNotifier::getDiskUsedSize() const
{
    Guard guard(_lock);
    return _diskUsedSizeBytes;
}

TransientResourceUsage
DiskMemUsageNotifier::get_transient_resource_usage() const
{
    Guard guard(_lock);
    return _transient_usage;
}

DiskMemUsageNotifier::Config
DiskMemUsageNotifier::getConfig() const
{
    Guard guard(_lock);
    return _config;
}

DiskMemUsageState
DiskMemUsageNotifier::usageState() const
{
    Guard guard(_lock);
    return _dmstate;
}

DiskMemUsageMetrics
DiskMemUsageNotifier::get_metrics() const
{
    Guard guard(_lock);
    DiskMemUsageMetrics result(_disk_mem_usage_metrics);
    _disk_mem_usage_metrics = DiskMemUsageMetrics(_dmstate);
    return result;
}

void
DiskMemUsageNotifier::addDiskMemUsageListener(IDiskMemUsageListener *listener)
{
    Guard guard(_lock);
    _listeners.push_back(listener);
    listener->notifyDiskMemUsage(_dmstate);
}

void
DiskMemUsageNotifier::removeDiskMemUsageListener(IDiskMemUsageListener *listener)
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
DiskMemUsageNotifier::notifyDiskMemUsage(const Guard &guard, DiskMemUsageState state)
{
    (void) guard;
    _dmstate = state;
    _disk_mem_usage_metrics.merge(state);
    _filter.notify_disk_mem_usage(_dmstate, _memoryStats, _diskUsedSizeBytes);
    for (const auto &listener : _listeners) {
        listener->notifyDiskMemUsage(_dmstate);
    }
}

} // namespace proton
