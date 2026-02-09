// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

namespace searchcorespi::common {

class ResourceUsage;

/**
 * Interface class providing a snapshot of resource usage.
 *
 * E.g. the transient memory used by the memory index and extra disk needed for running disk index fusion.
 * This provides the total resource usage for the components this provider encapsulates.
 */
class IResourceUsageProvider {
public:
    virtual ~IResourceUsageProvider() = default;
    virtual ResourceUsage get_resource_usage() const = 0;
};

}
