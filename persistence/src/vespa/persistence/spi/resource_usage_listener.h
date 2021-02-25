// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_resource_usage_listener.h"
#include "resource_usage.h"
#include <memory>

namespace vespalib { class IDestructorCallback; }

namespace storage::spi {

/*
 * Class for listening to resource usage updates.
 */
class ResourceUsageListener : public IResourceUsageListener
{
    ResourceUsage _usage;
    std::unique_ptr<vespalib::IDestructorCallback> _register_guard;
public:
    ResourceUsageListener();
    ~ResourceUsageListener() override;
    void update_resource_usage(const ResourceUsage& resource_usage) override;
    const ResourceUsage& get_usage() const noexcept { return _usage; }
    void set_register_guard(std::unique_ptr<vespalib::IDestructorCallback> register_guard);
    void reset();
};

}
