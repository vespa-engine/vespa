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

AddressSpaceUsageStats::AddressSpaceUsageStats(const AddressSpaceUsageStats&) = default;

AddressSpaceUsageStats::~AddressSpaceUsageStats() = default;

AddressSpaceUsageStats& AddressSpaceUsageStats::operator=(const AddressSpaceUsageStats&) = default;

bool
AddressSpaceUsageStats::less_usage_than(const vespalib::AddressSpace& usage,
                                        const std::string& attributeName,
                                        const std::string& component_name,
                                        const std::string& subDbName) const noexcept
{
    if (_attributeName.empty()) {
        return true;
    }
    // Prefer the highest usage, then lowest subdb name, lowest attribute name, lowest component name
    auto old_usage = _usage.usage();
    auto new_usage = usage.usage();
    if (old_usage != new_usage) {
        return old_usage < new_usage;
    }
    if (_subDbName != subDbName) {
        return _subDbName > subDbName;
    }
    if (_attributeName != attributeName) {
        return _attributeName > attributeName;
    }
    return _component_name > component_name;
}

void
AddressSpaceUsageStats::merge(const vespalib::AddressSpace &usage,
                              const std::string &attributeName,
                              const std::string &component_name,
                              const std::string &subDbName)
{
    if (less_usage_than(usage, attributeName, component_name, subDbName)) {
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
