// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_stats.h"
#include <vespa/searchlib/attribute/address_space_components.h>
#include <ostream>

using search::AddressSpaceComponents;

namespace proton {

AttributeUsageStats::AttributeUsageStats()
    : AttributeUsageStats("")
{
}

AttributeUsageStats::AttributeUsageStats(const std::string& document_type_in)
    : _max_usage(vespalib::AddressSpace()),
      _document_type(document_type_in)
{
}

AttributeUsageStats::AttributeUsageStats(const AttributeUsageStats&) = default;

AttributeUsageStats::~AttributeUsageStats() = default;

AttributeUsageStats& AttributeUsageStats::operator=(const AttributeUsageStats&) = default;

void
AttributeUsageStats::merge(const search::AddressSpaceUsage &usage,
                           const std::string &attributeName,
                           const std::string &subDbName)
{
    for (const auto& entry : usage.get_all()) {
        _max_usage.merge(entry.second, attributeName, entry.first, subDbName);
    }
}

std::ostream&
operator<<(std::ostream& out, const AttributeUsageStats& rhs)
{
    out << "{doctype=" << rhs.document_type() << ", max_address_space_usage=" << rhs.max_address_space_usage() << "}";
    return out;
}

} // namespace proton
