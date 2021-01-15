// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/vespalib/stllike/string.h>

namespace storage::spi {

class ResourceUsage;

/*
 * Interface class for listening to resource usage updates.
 */
class IResourceUsageListener
{
public:
    virtual ~IResourceUsageListener() = default;
    virtual void update_resource_usage(const ResourceUsage& resource_usage) = 0;
};

}
