// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_resource_usage.h"
#include <iostream>

namespace storage::spi {

std::ostream& operator<<(std::ostream& out, const AttributeResourceUsage& attribute_resource_usage)
{
    out << "{usage=" << attribute_resource_usage.get_usage() <<
        ", name=" << attribute_resource_usage.get_name() << "}";
    return out;
}

}
