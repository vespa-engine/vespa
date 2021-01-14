// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/i_disk_mem_usage_listener.h>
#include <vespa/persistence/spi/resource_usage.h>
#include <mutex>
#include <memory>
#include <vector>

namespace storage::spi { class IResourceUsageListener; }

namespace proton {

class DiskMemUsageState;
class IDiskMemUsageNotifier;

/*
 * Class tracking resource usage for persistence provider.
 */
class ResourceUsageTracker : public IDiskMemUsageListener
{
    using ListenerSP = std::shared_ptr<storage::spi::IResourceUsageListener>;
    std::mutex                  _lock;
    storage::spi::ResourceUsage _resource_usage;
    std::vector<ListenerSP>     _listeners;
    IDiskMemUsageNotifier&      _disk_mem_usage_notifier;
public:
    ResourceUsageTracker(IDiskMemUsageNotifier& notifier);
    ~ResourceUsageTracker() override;
    void notifyDiskMemUsage(DiskMemUsageState state) override;
    void add_listener(ListenerSP listener);
    void remove_listener(ListenerSP listener);
};

}
