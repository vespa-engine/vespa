// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_notifier.h"

#include "i_resource_usage_listener.h"
#include "resource_usage_write_filter.h"

#include <vespa/vespalib/util/hw_info.h>

#include <algorithm>

using searchcorespi::common::ResourceUsage;

namespace proton {

ResourceUsageNotifier::ResourceUsageNotifier(ResourceUsageWriteFilter& filter)
    : _lock(),
      _hwInfo(filter.get_hw_info()),
      _memoryStats(),
      _disk_usage(0, _hwInfo.disk().sizeBytes()),
      _reserved_disk_space_and_memory(),
      _resource_usage(),
      _attribute_usage(),
      _config(),
      _usage_state(),
      _disk_mem_usage_metrics(),
      _listeners(),
      _filter(filter) {
}

ResourceUsageNotifier::~ResourceUsageNotifier() = default;

void ResourceUsageNotifier::recalcState(const Guard& guard, bool disk_mem_sample) {
    double memoryUsed = getMemoryUsedRatio(guard);
    double diskUsed = getDiskUsedRatio(guard);
    double attribute_address_space_used = _attribute_usage.max_address_space_usage().getUsage().usage();
    double reserved_disk_space = get_relative_reserved_disk_space(guard);
    double transient_disk_usage =
        std::min(std::min(get_relative_transient_disk_usage(guard), diskUsed), reserved_disk_space);
    double non_transient_disk_usage = diskUsed - transient_disk_usage;
    double reported_disk =
        std::max(diskUsed, non_transient_disk_usage + _config._reserved_disk_space_factor * reserved_disk_space);
    double reserved_memory = get_relative_reserved_memory(guard);
    double transient_memory_usage =
        std::min(std::min(get_relative_transient_memory_usage(guard), memoryUsed), reserved_memory);
    double non_transient_memory_usage = memoryUsed - transient_memory_usage;
    double reported_memory =
        std::max(memoryUsed, non_transient_memory_usage + _config._reserved_memory_factor * reserved_memory);
    ResourceUsageState usage(
        ResourceUsageWithLimit(reported_disk, _config._diskLimit),
        ResourceUsageWithLimit(reported_memory, _config._memoryLimit), non_transient_disk_usage,
        non_transient_memory_usage, reserved_disk_space, _config._reserved_disk_space_factor, reserved_memory,
        _config._reserved_memory_factor, transient_disk_usage, transient_memory_usage,
        ResourceUsageWithLimit(attribute_address_space_used, _config._attribute_limit._address_space_limit),
        _attribute_usage);
    notify_resource_usage(guard, usage, disk_mem_sample);
}

double ResourceUsageNotifier::getMemoryUsedRatio(const Guard&) const {
    uint64_t unscaledMemoryUsed = _memoryStats.getAnonymousRss();
    return static_cast<double>(unscaledMemoryUsed) / _hwInfo.memory().sizeBytes();
}

double ResourceUsageNotifier::getDiskUsedRatio(const Guard&) const {
    return static_cast<double>(_disk_usage.used_bytes()) / static_cast<double>(_disk_usage.capacity_bytes());
}

double ResourceUsageNotifier::get_relative_reserved_disk_space(const Guard&) const {
    return static_cast<double>(_reserved_disk_space_and_memory.reserved_disk_space()) / _disk_usage.capacity_bytes();
}

double ResourceUsageNotifier::get_relative_reserved_memory(const Guard&) const {
    return static_cast<double>(_reserved_disk_space_and_memory.reserved_memory()) / _hwInfo.memory().sizeBytes();
}

double ResourceUsageNotifier::get_relative_transient_memory_usage(const Guard&) const {
    return static_cast<double>(_resource_usage.transient_memory()) / _hwInfo.memory().sizeBytes();
}

double ResourceUsageNotifier::get_relative_transient_disk_usage(const Guard&) const {
    return static_cast<double>(_resource_usage.transient_disk()) / _disk_usage.capacity_bytes();
}

void ResourceUsageNotifier::set_resource_usage(const ResourceUsage&         resource_usage,
                                               vespalib::ProcessMemoryStats memoryStats, const DiskUsage& disk_usage,
                                               ReservedDiskSpaceAndMemory reserved_disk_space_and_memory_) {
    Guard guard(_lock);
    _resource_usage = resource_usage;
    _reserved_disk_space_and_memory = reserved_disk_space_and_memory_;
    _memoryStats = memoryStats;
    _disk_usage = disk_usage;
    recalcState(guard, true);
}

void ResourceUsageNotifier::notify_attribute_usage(const AttributeUsageStats& attribute_usage) {
    Guard guard(_lock);
    _attribute_usage = attribute_usage;
    recalcState(guard, false);
}

bool ResourceUsageNotifier::setConfig(Config config_in) {
    Guard guard(_lock);
    if (_config == config_in)
        return false;
    _config = config_in;
    recalcState(guard, false);
    return true;
}

vespalib::ProcessMemoryStats ResourceUsageNotifier::getMemoryStats() const {
    Guard guard(_lock);
    return _memoryStats;
}

DiskUsage ResourceUsageNotifier::disk_usage() const {
    Guard guard(_lock);
    return _disk_usage;
}

ReservedDiskSpaceAndMemory ResourceUsageNotifier::reserved_disk_space_and_memory() const noexcept {
    Guard guard(_lock);
    return _reserved_disk_space_and_memory;
}

ResourceUsage ResourceUsageNotifier::get_resource_usage() const {
    Guard guard(_lock);
    return _resource_usage;
}

ResourceUsageNotifier::Config ResourceUsageNotifier::getConfig() const {
    Guard guard(_lock);
    return _config;
}

ResourceUsageState ResourceUsageNotifier::usageState() const {
    Guard guard(_lock);
    return _usage_state;
}

DiskMemUsageMetrics ResourceUsageNotifier::get_metrics() const {
    Guard               guard(_lock);
    DiskMemUsageMetrics result(_disk_mem_usage_metrics);
    _disk_mem_usage_metrics = DiskMemUsageMetrics(_usage_state);
    return result;
}

void ResourceUsageNotifier::add_resource_usage_listener(IResourceUsageListener* listener) {
    Guard guard(_lock);
    _listeners.push_back(listener);
    listener->notify_resource_usage(_usage_state);
}

void ResourceUsageNotifier::remove_resource_usage_listener(IResourceUsageListener* listener) {
    Guard guard(_lock);
    for (auto itr = _listeners.begin(); itr != _listeners.end(); ++itr) {
        if (*itr == listener) {
            _listeners.erase(itr);
            break;
        }
    }
}

void ResourceUsageNotifier::notify_resource_usage(const Guard& guard, ResourceUsageState state,
                                                  bool disk_mem_sample) {
    (void)guard;
    _usage_state = state;
    if (disk_mem_sample) {
        _disk_mem_usage_metrics.merge(state);
    }
    _filter.notify_resource_usage(_usage_state, _memoryStats, _disk_usage);
    for (const auto& listener : _listeners) {
        listener->notify_resource_usage(_usage_state);
    }
}

} // namespace proton
