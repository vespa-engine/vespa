// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_usage_stats.h"

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


} // namespace proton
