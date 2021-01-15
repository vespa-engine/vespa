// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_tracker.h"
#include <vespa/searchcore/proton/server/disk_mem_usage_state.h>
#include <vespa/searchcore/proton/server/i_disk_mem_usage_notifier.h>
#include <vespa/persistence/spi/i_resource_usage_listener.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <cassert>

using storage::spi::ResourceUsage;

namespace proton {

class ResourceUsageTracker::ListenerGuard : public vespalib::IDestructorCallback
{
    std::weak_ptr<ResourceUsageTracker> _tracker;
public:
    ListenerGuard(std::shared_ptr<ResourceUsageTracker> tracker);
    ~ListenerGuard() override;
};

ResourceUsageTracker::ListenerGuard::ListenerGuard(std::shared_ptr<ResourceUsageTracker> tracker)
    : _tracker(tracker)
{
}

ResourceUsageTracker::ListenerGuard::~ListenerGuard()
{
    auto tracker = _tracker.lock();
    if (tracker) {
        tracker->remove_listener();
    }
}

ResourceUsageTracker::ResourceUsageTracker(IDiskMemUsageNotifier& disk_mem_usage_notifier)
    : std::enable_shared_from_this<ResourceUsageTracker>(),
      IDiskMemUsageListener(),
      _lock(),
      _resource_usage(),
      _listener(nullptr),
      _disk_mem_usage_notifier(disk_mem_usage_notifier)
{
    _disk_mem_usage_notifier.addDiskMemUsageListener(this);
}

ResourceUsageTracker::~ResourceUsageTracker()
{
    _disk_mem_usage_notifier.removeDiskMemUsageListener(this);
}

void
ResourceUsageTracker::notifyDiskMemUsage(DiskMemUsageState state)
{
    std::lock_guard guard(_lock);
    _resource_usage = ResourceUsage(state.diskState().usage(), state.memoryState().usage());
    if (_listener != nullptr) {
        _listener->update_resource_usage(_resource_usage);
    }
}

std::unique_ptr<vespalib::IDestructorCallback>
ResourceUsageTracker::set_listener(storage::spi::IResourceUsageListener& listener)
{
    std::lock_guard guard(_lock);
    assert(_listener == nullptr);
    _listener = &listener;
    listener.update_resource_usage(_resource_usage);
    return std::make_unique<ListenerGuard>(shared_from_this());
}

void
ResourceUsageTracker::remove_listener()
{
    std::lock_guard guard(_lock);
    _listener = nullptr;
}

};

