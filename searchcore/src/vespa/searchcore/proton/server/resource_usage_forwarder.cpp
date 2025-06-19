// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_forwarder.h"
#include <vespa/vespalib/util/lambdatask.h>

using vespalib::makeLambdaTask;

namespace proton {

ResourceUsageForwarder::ResourceUsageForwarder(searchcorespi::index::IThreadService &executor)
    : IResourceUsageNotifier(),
      IResourceUsageListener(),
      _executor(executor),
      _listeners(),
      _state()
{ }

ResourceUsageForwarder::~ResourceUsageForwarder() = default;

void
ResourceUsageForwarder::add_resource_usage_listener(IResourceUsageListener *listener)
{
    std::lock_guard guard(_lock);
    _listeners.push_back(listener);
    listener->notify_resource_usage(_state);
}

void
ResourceUsageForwarder::remove_resource_usage_listener(IResourceUsageListener *listener)
{
    std::lock_guard guard(_lock);
    for (auto itr = _listeners.begin(); itr != _listeners.end(); ++itr) {
        if (*itr == listener) {
            _listeners.erase(itr);
            break;
        }
    }
}

void
ResourceUsageForwarder::notify_resource_usage(const ResourceUsageState& state)
{
    _executor.execute(makeLambdaTask([this, state]() { forward(state); }));
}


void
ResourceUsageForwarder::forward(ResourceUsageState state)
{
    std::lock_guard guard(_lock);
    if (_state != state) {
        _state = state;
        for (const auto &listener : _listeners) {
            listener->notify_resource_usage(state);
        }
    }
}

} // namespace proton
