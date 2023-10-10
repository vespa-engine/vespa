// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "address_space_usage_stats.h"
#include <ostream>

namespace proton {

AddressSpaceUsageStats::AddressSpaceUsageStats(const vespalib::AddressSpace & usage)
    : _usage(usage),
      _attributeName(),
      _component_name(),
      _subDbName()
{
}

AddressSpaceUsageStats::~AddressSpaceUsageStats() = default;

void
AddressSpaceUsageStats::merge(const vespalib::AddressSpace &usage,
                              const vespalib::string &attributeName,
                              const vespalib::string &component_name,
                              const vespalib::string &subDbName)
{
    if (attributeName.empty() || usage.usage() > _usage.usage()) {
        _usage = usage;
        _attributeName = attributeName;
        _component_name = component_name;
        _subDbName = subDbName;
    }
}

std::ostream&
operator<<(std::ostream& out, const AddressSpaceUsageStats& rhs)
{
    out << "{usage=" << rhs.getUsage() <<
           ", attribute_name=" << rhs.getAttributeName() <<
           ", component_name=" << rhs.get_component_name() <<
           ", subdb_name=" << rhs.getSubDbName() << "}";
    return out;
}

} // namespace proton
