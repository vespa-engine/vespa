// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/i_resource_usage_notifier.h>
#include <vespa/searchcore/proton/server/i_resource_usage_listener.h>
#include <vector>

namespace proton::test {

/**
 * Test notifier for resource usage.
 */
class ResourceUsageNotifier : public IResourceUsageNotifier
{
    std::vector<IResourceUsageListener *> _listeners;
    ResourceUsageState _state;
public:
    ResourceUsageNotifier(ResourceUsageState state)
        : IResourceUsageNotifier(),
          _listeners(),
          _state(state)
    {
    }
    ResourceUsageNotifier()
        : ResourceUsageNotifier(ResourceUsageState())
    {
    }
    virtual ~ResourceUsageNotifier();
    void add_resource_usage_listener(IResourceUsageListener *listener) override {
        _listeners.push_back(listener);
        listener->notify_resource_usage(_state);
    }
    void remove_resource_usage_listener(IResourceUsageListener *listener) override {
        for (auto itr = _listeners.begin(); itr != _listeners.end(); ++itr) {
            if (*itr == listener) {
                _listeners.erase(itr);
                break;
            }
        }
    }
    void notify(ResourceUsageState state) {
        if (_state != state) {
            _state = state;
            for (const auto &listener : _listeners) {
                listener->notify_resource_usage(state);
            }
        }
    }
};

}
