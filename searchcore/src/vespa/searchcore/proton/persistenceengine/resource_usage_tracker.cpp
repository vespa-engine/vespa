// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_tracker.h"
#include <vespa/searchcore/proton/server/disk_mem_usage_state.h>
#include <vespa/searchcore/proton/server/i_disk_mem_usage_notifier.h>
#include <vespa/persistence/spi/i_resource_usage_listener.h>
#include <cassert>

namespace proton {

ResourceUsageTracker::ResourceUsageTracker(IDiskMemUsageNotifier& disk_mem_usage_notifier)
    : _lock(),
      _resource_usage(),
      _listeners(),
      _disk_mem_usage_notifier(disk_mem_usage_notifier)
{
    _disk_mem_usage_notifier.addDiskMemUsageListener(this);
}

ResourceUsageTracker::~ResourceUsageTracker()
{
    _disk_mem_usage_notifier.removeDiskMemUsageListener(this);
    std::lock_guard guard(_lock);
    assert(_listeners.empty());
}

void
ResourceUsageTracker::notifyDiskMemUsage(DiskMemUsageState state)
{
    std::lock_guard guard(_lock);
    _resource_usage.set_disk_usage(state.diskState().usage());
    _resource_usage.set_memory_usage(state.memoryState().usage());
    for (auto& listener : _listeners) {
        listener->update_resource_usage(_resource_usage);
    }
}

void
ResourceUsageTracker::add_listener(ListenerSP listener)
{
    std::lock_guard guard(_lock);
    _listeners.push_back(listener);
    listener->update_resource_usage(_resource_usage);
}

void
ResourceUsageTracker::remove_listener(ListenerSP listener)
{
    std::lock_guard guard(_lock);
    for (auto itr = _listeners.begin(); itr != _listeners.end(); ++itr) {
        if (*itr == listener) {
            _listeners.erase(itr);
            break;
        }
    }
}

};

