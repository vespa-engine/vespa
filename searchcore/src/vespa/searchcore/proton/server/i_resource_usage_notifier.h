// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace proton {

class IResourceUsageListener;

/**
 * Interface used to request notification when resource usage state
 * has changed.
 */
class IResourceUsageNotifier
{
public:
    virtual ~IResourceUsageNotifier() = default;
    virtual void add_resource_usage_listener(IResourceUsageListener *listener) = 0;
    virtual void remove_resource_usage_listener(IResourceUsageListener *listener) = 0;
};

} // namespace proton
