// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_stats.h"
#include <iostream>

namespace proton {

AttributeUsageStats::AttributeUsageStats()
    : _enumStoreUsage(search::AddressSpaceUsage::defaultEnumStoreUsage()),
      _multiValueUsage(search::AddressSpaceUsage::defaultMultiValueUsage())
{
}

void
AttributeUsageStats::merge(const search::AddressSpaceUsage &usage,
                           const vespalib::string &attributeName,
                           const vespalib::string &subDbName)
{
    _enumStoreUsage.merge(usage.enumStoreUsage(), attributeName, subDbName);
    _multiValueUsage.merge(usage.multiValueUsage(), attributeName, subDbName);
}

std::ostream&
operator<<(std::ostream& out, const AttributeUsageStats& rhs)
{
    out << "{enum_store=" << rhs.enumStoreUsage() <<
            ", multi_value=" << rhs.multiValueUsage() << "}";
    return out;
}

} // namespace proton
