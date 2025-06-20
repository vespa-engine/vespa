// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_tracker.h"
#include <vespa/searchcore/proton/server/resource_usage_state.h>
#include <vespa/searchcore/proton/server/i_resource_usage_notifier.h>
#include <vespa/persistence/spi/i_resource_usage_listener.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <cassert>

using storage::spi::AttributeResourceUsage;
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

ResourceUsageTracker::ResourceUsageTracker(IResourceUsageNotifier& resource_usage_notifier)
    : std::enable_shared_from_this<ResourceUsageTracker>(),
      IResourceUsageListener(),
      _lock(),
      _resource_usage(),
      _listener(nullptr),
      _resource_usage_notifier(resource_usage_notifier)
{
    _resource_usage_notifier.add_resource_usage_listener(this);
}

ResourceUsageTracker::~ResourceUsageTracker()
{
    _resource_usage_notifier.remove_resource_usage_listener(this);
}

storage::spi::ResourceUsage
ResourceUsageTracker::get_resource_usage() const
{
    std::lock_guard guard(_lock);
    return _resource_usage;
}

void
ResourceUsageTracker::notify_resource_usage(const ResourceUsageState& state)
{
    std::lock_guard guard(_lock);
    std::string name;
    auto& attribute_usage = state.attribute_usage();
    if (!attribute_usage.document_type().empty()) {
        auto& max = attribute_usage.max_address_space_usage();
        name = attribute_usage.document_type() + "." + max.getSubDbName() + "." + max.getAttributeName() + "." +
               max.get_component_name();
    }
    // The transient resource usage is subtracted from the total resource usage
    // before it eventually is reported to the cluster controller (to decide whether to block client feed).
    // This ensures that the transient resource usage is covered by the resource headroom on the content node,
    // instead of leading to feed blocked due to natural fluctuations.
    _resource_usage = ResourceUsage(state.non_transient_disk_usage(),
                                    state.non_transient_memory_usage(),
                                    AttributeResourceUsage(attribute_usage.max_address_space_usage().getUsage().usage(),
                                                           name));
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
