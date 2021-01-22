// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "resource_usage.h"
#include <iostream>

namespace storage::spi {

ResourceUsage::~ResourceUsage() = default;

std::ostream& operator<<(std::ostream& out, const ResourceUsage& resource_usage)
{
    out << "{disk_usage=" << resource_usage.get_disk_usage() <<
        ", memory_usage=" << resource_usage.get_memory_usage() <<
        ", attribute_enum_store_usage=" << resource_usage.get_attribute_enum_store_usage() <<
        ", attribute_multivalue_usage=" << resource_usage.get_attribute_multivalue_usage() << "}";
    return out;
}

}
