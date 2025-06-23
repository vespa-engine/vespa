// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "resource_usage_with_limit.h"
#include <vespa/searchcore/proton/attribute/attribute_usage_stats.h>
#include <algorithm>

namespace proton {

/**
 * Class used to describe state of resource usage relative to configured limits.
 * In addition relative transient disk and memory usage are tracked.
 */
class ResourceUsageState
{
    ResourceUsageWithLimit _diskState;
    ResourceUsageWithLimit _memoryState;
    double                 _transient_disk_usage;
    double                 _transient_memory_usage;
    ResourceUsageWithLimit _max_attribute_address_space_state;
    AttributeUsageStats    _attribute_usage;

public:
    ResourceUsageState();
    ResourceUsageState(const ResourceUsageWithLimit &diskState_,
                       const ResourceUsageWithLimit &memoryState_,
                       double transient_disk_usage_ = 0,
                       double transient_memory_usage_ = 0);
    ResourceUsageState(const ResourceUsageWithLimit &diskState_,
                       const ResourceUsageWithLimit &memoryState_,
                       double transient_disk_usage_,
                       double transient_memory_usage_,
                       const ResourceUsageWithLimit& max_attribute_address_space_state,
                       const AttributeUsageStats& attribute_usage);
    ~ResourceUsageState();
    bool operator==(const ResourceUsageState &rhs) const;
    bool operator!=(const ResourceUsageState &rhs) const;
    const ResourceUsageWithLimit &diskState() const noexcept { return _diskState; }
    const ResourceUsageWithLimit &memoryState() const noexcept { return _memoryState; }
    double transient_disk_usage() const noexcept { return _transient_disk_usage; }
    double transient_memory_usage() const noexcept { return _transient_memory_usage; }
    double non_transient_disk_usage() const { return std::max(0.0, _diskState.usage() - _transient_disk_usage); }
    double non_transient_memory_usage() const { return std::max(0.0, _memoryState.usage() - _transient_memory_usage); }
    bool aboveDiskLimit(double resourceLimitFactor) const { return diskState().aboveLimit(resourceLimitFactor); }
    bool aboveMemoryLimit(double resourceLimitFactor) const { return memoryState().aboveLimit(resourceLimitFactor); }
    const ResourceUsageWithLimit& max_attribute_address_space_state() const noexcept {
        return _max_attribute_address_space_state;
    }
    const AttributeUsageStats& attribute_usage() const noexcept { return _attribute_usage; }
};

} // namespace proton
