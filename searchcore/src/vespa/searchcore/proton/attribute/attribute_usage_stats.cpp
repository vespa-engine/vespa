// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_stats.h"
#include <vespa/searchlib/attribute/address_space_components.h>
#include <iostream>

using search::AddressSpaceComponents;

namespace proton {

AttributeUsageStats::AttributeUsageStats()
    : _max_usage(vespalib::AddressSpace())
{
}

AttributeUsageStats::~AttributeUsageStats() = default;

void
AttributeUsageStats::merge(const search::AddressSpaceUsage &usage,
                           const vespalib::string &attributeName,
                           const vespalib::string &subDbName)
{
    for (const auto& entry : usage.get_all()) {
        _max_usage.merge(entry.second, attributeName, entry.first, subDbName);
    }
}

std::ostream&
operator<<(std::ostream& out, const AttributeUsageStats& rhs)
{
    out << "{max_address_space_usage=" << rhs.max_address_space_usage() << "}";
    return out;
}

} // namespace proton
