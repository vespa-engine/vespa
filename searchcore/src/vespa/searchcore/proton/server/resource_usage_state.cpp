// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage_state.h"

namespace proton {

ResourceUsageState::ResourceUsageState()
    : ResourceUsageState(ResourceUsageWithLimit(), ResourceUsageWithLimit(), 0.0, 0.0, 0.0, 0.0,
                         ResourceUsageWithLimit(), AttributeUsageStats())
{
}

ResourceUsageState::ResourceUsageState(const ResourceUsageWithLimit &diskState_,
                                       const ResourceUsageWithLimit &memoryState_)
    : ResourceUsageState(diskState_, memoryState_, 0.0, 0.0, 0.0, 0.0,
                         ResourceUsageWithLimit(), AttributeUsageStats())
{
}

ResourceUsageState::ResourceUsageState(const ResourceUsageWithLimit &diskState_,
                                       const ResourceUsageWithLimit &memoryState_,
                                       double reserved_disk_space_,
                                       double reserved_disk_space_factor_,
                                       double transient_disk_usage_,
                                       double transient_memory_usage_)
    : ResourceUsageState(diskState_, memoryState_, reserved_disk_space_, reserved_disk_space_factor_,
                         transient_disk_usage_, transient_memory_usage_,
                         ResourceUsageWithLimit(), AttributeUsageStats())
{
}

ResourceUsageState::ResourceUsageState(const ResourceUsageWithLimit &diskState_,
                                       const ResourceUsageWithLimit &memoryState_,
                                       double reserved_disk_space_,
                                       double reserved_disk_space_factor_,
                                       double transient_disk_usage_,
                                       double transient_memory_usage_,
                                       const ResourceUsageWithLimit& max_attribute_address_space_state,
                                       const AttributeUsageStats& attribute_usage)
    : _diskState(diskState_),
      _memoryState(memoryState_),
      _reserved_disk_space(reserved_disk_space_),
      _reserved_disk_space_factor(reserved_disk_space_factor_),
      _transient_disk_usage(transient_disk_usage_),
      _transient_memory_usage(transient_memory_usage_),
      _max_attribute_address_space_state(max_attribute_address_space_state),
      _attribute_usage(attribute_usage)
{
}

ResourceUsageState::~ResourceUsageState() = default;

bool
ResourceUsageState::operator==(const ResourceUsageState &rhs) const {
    return ((_diskState == rhs._diskState) &&
            (_memoryState == rhs._memoryState) &&
            (_transient_disk_usage == rhs._transient_disk_usage) &&
            (_transient_memory_usage == rhs._transient_memory_usage) &&
            (_max_attribute_address_space_state == rhs._max_attribute_address_space_state) &&
            (_attribute_usage == rhs._attribute_usage));
}

bool
ResourceUsageState::operator!=(const ResourceUsageState &rhs) const {
    return ! ((*this) == rhs);
}

}
