// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_resource_usage_notifier.h"
#include "i_resource_usage_listener.h"
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vector>
#include <mutex>

namespace proton {

/**
 * Forwarder for resource usage state changes. Notification is forwarded
 * as a task run by the supplied executor.
 */
class ResourceUsageForwarder : public IResourceUsageNotifier,
                               public IResourceUsageListener
{
    searchcorespi::index::IThreadService &_executor;
    std::vector<IResourceUsageListener *> _listeners;
    std::mutex        _lock;
    ResourceUsageState _state;
    void forward(ResourceUsageState state);
public:
    ResourceUsageForwarder(searchcorespi::index::IThreadService &executor);
    ~ResourceUsageForwarder() override;
    void add_resource_usage_listener(IResourceUsageListener *listener) override;
    void remove_resource_usage_listener(IResourceUsageListener *listener) override;
    void notify_resource_usage(const ResourceUsageState& state) override;
};

} // namespace proton
