// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/i_resource_usage_listener.h>
#include <vespa/persistence/spi/resource_usage.h>
#include <mutex>
#include <memory>

namespace storage::spi { class IResourceUsageListener; }
namespace vespalib { class IDestructorCallback; }

namespace proton {

class ResourceUsageState;
class IResourceUsageNotifier;

/*
 * Class tracking resource usage for persistence provider.
 */
class ResourceUsageTracker : public std::enable_shared_from_this<ResourceUsageTracker>,
                             public IResourceUsageListener
{
    class ListenerGuard;
    mutable std::mutex          _lock;
    storage::spi::ResourceUsage _resource_usage;
    storage::spi::IResourceUsageListener* _listener;
    IResourceUsageNotifier&      _resource_usage_notifier;
    void remove_listener();
public:
    ResourceUsageTracker(IResourceUsageNotifier& resource_usage_notifier);
    ~ResourceUsageTracker() override;
    storage::spi::ResourceUsage get_resource_usage() const;
    void notify_resource_usage(const ResourceUsageState& state) override;
    std::unique_ptr<vespalib::IDestructorCallback> set_listener(storage::spi::IResourceUsageListener& listener);
};

}
