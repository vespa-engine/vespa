// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/i_disk_mem_usage_listener.h>
#include <vespa/persistence/spi/resource_usage.h>
#include <mutex>
#include <memory>
#include <vector>

namespace storage::spi { class IResourceUsageListener; }
namespace vespalib { class IDestructorCallback; }

namespace proton {

class DiskMemUsageState;
class IDiskMemUsageNotifier;

/*
 * Class tracking resource usage for persistence provider.
 */
class ResourceUsageTracker : public std::enable_shared_from_this<ResourceUsageTracker>, public IDiskMemUsageListener
{
    class ListenerGuard;
    std::mutex                  _lock;
    storage::spi::ResourceUsage _resource_usage;
    storage::spi::IResourceUsageListener* _listener;
    IDiskMemUsageNotifier&      _disk_mem_usage_notifier;
    void remove_listener();
public:
    ResourceUsageTracker(IDiskMemUsageNotifier& notifier);
    ~ResourceUsageTracker() override;
    void notifyDiskMemUsage(DiskMemUsageState state) override;
    std::unique_ptr<vespalib::IDestructorCallback> set_listener(storage::spi::IResourceUsageListener& listener);
};

}
