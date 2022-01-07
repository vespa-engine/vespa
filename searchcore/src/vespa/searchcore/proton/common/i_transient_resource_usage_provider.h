// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstddef>

namespace proton {

/**
 * Interface class providing a snapshot of transient resource usage.
 *
 * E.g. the memory used by the memory index and extra disk needed for running disk index fusion.
 * This provides the total transient resource usage for the components this provider encapsulates.
 */
class ITransientResourceUsageProvider {
public:
    virtual ~ITransientResourceUsageProvider() = default;
    virtual size_t get_transient_memory_usage() const = 0;
    virtual size_t get_transient_disk_usage() const = 0;
};

}
