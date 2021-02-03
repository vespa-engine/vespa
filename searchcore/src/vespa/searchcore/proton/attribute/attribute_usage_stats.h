// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "address_space_usage_stats.h"
#include <vespa/searchlib/attribute/address_space_usage.h>

namespace proton {

/**
 * Class representing aggregated attribute usage, with info about
 * the most bloated attributes with regards to enum store and
 * multivalue mapping.
 */
class AttributeUsageStats
{
    AddressSpaceUsageStats _enumStoreUsage;
    AddressSpaceUsageStats _multiValueUsage;

public:
    AttributeUsageStats();
    void merge(const search::AddressSpaceUsage &usage,
               const vespalib::string &attributeName,
               const vespalib::string &subDbName);

    const AddressSpaceUsageStats& enumStoreUsage() const { return _enumStoreUsage; }
    const AddressSpaceUsageStats& multiValueUsage() const { return _multiValueUsage; }

    bool operator==(const AttributeUsageStats& rhs) const {
        return (_enumStoreUsage == rhs._enumStoreUsage) &&
                (_multiValueUsage == rhs._multiValueUsage);
    }
};

std::ostream& operator<<(std::ostream& out, const AttributeUsageStats& rhs);

} // namespace proton
