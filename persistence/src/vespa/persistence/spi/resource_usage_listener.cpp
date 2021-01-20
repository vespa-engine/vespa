// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_listener.h"
#include <vespa/vespalib/util/idestructorcallback.h>

namespace storage::spi {

ResourceUsageListener::ResourceUsageListener()
    : IResourceUsageListener(),
      _usage(),
      _register_guard()
{
}

ResourceUsageListener::~ResourceUsageListener()
{
    reset();
}

void
ResourceUsageListener::update_resource_usage(const ResourceUsage& resource_usage)
{
    _usage = resource_usage;
}

void
ResourceUsageListener::set_register_guard(std::unique_ptr<vespalib::IDestructorCallback> register_guard)
{
    _register_guard = std::move(register_guard);
}

void
ResourceUsageListener::reset()
{
    _register_guard.reset();
}

}
