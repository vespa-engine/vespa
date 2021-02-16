// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "attribute_resource_usage.h"

namespace storage::spi {

/*
 * Class representing resource usage for persistence provider.
 * Numbers are normalized to be between 0.0 and 1.0
 */
class ResourceUsage
{
    double _disk_usage;
    double _memory_usage;
    AttributeResourceUsage _attribute_enum_store_usage;
    AttributeResourceUsage _attribute_multivalue_usage;

public:
    
    ResourceUsage(double disk_usage, double memory_usage, const AttributeResourceUsage &attribute_enum_store_usage, const AttributeResourceUsage &attribute_multivalue_usage)
        : _disk_usage(disk_usage),
          _memory_usage(memory_usage),
          _attribute_enum_store_usage(attribute_enum_store_usage),
          _attribute_multivalue_usage(attribute_multivalue_usage)
    {
    }

    ResourceUsage(double disk_usage, double memory_usage)
        : ResourceUsage(disk_usage, memory_usage, AttributeResourceUsage(), AttributeResourceUsage())
    {
    }

    ResourceUsage()
        : ResourceUsage(0.0, 0.0)
    {
    }

    ResourceUsage(const ResourceUsage &rhs);

    ResourceUsage(ResourceUsage &&rhs);

    ~ResourceUsage();

    ResourceUsage& operator=(const ResourceUsage &rhs);

    ResourceUsage& operator=(ResourceUsage &&rhs);

    double get_disk_usage() const noexcept { return _disk_usage; }
    double get_memory_usage() const noexcept { return _memory_usage; }
    const AttributeResourceUsage& get_attribute_enum_store_usage() const noexcept { return _attribute_enum_store_usage; }
    const AttributeResourceUsage& get_attribute_multivalue_usage() const noexcept { return _attribute_multivalue_usage; }

    bool operator==(const ResourceUsage &rhs) const noexcept {
        return ((_disk_usage == rhs._disk_usage) &&
                (_memory_usage == rhs._memory_usage) &&
                (_attribute_enum_store_usage == rhs._attribute_enum_store_usage) &&
                (_attribute_multivalue_usage == rhs._attribute_multivalue_usage));
    }
    bool operator!=(const ResourceUsage &rhs) const noexcept {
        return !operator==(rhs);
    }
};

std::ostream& operator<<(std::ostream& out, const ResourceUsage& resource_usage);

}
