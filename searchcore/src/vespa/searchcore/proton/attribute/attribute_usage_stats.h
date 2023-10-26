// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "address_space_usage_stats.h"
#include <vespa/searchlib/attribute/address_space_usage.h>

namespace proton {

/**
 * Class representing aggregated max address space usage
 * among components in attributes vectors in all sub databases.
 */
class AttributeUsageStats
{
    AddressSpaceUsageStats _max_usage;

public:
    AttributeUsageStats();
    ~AttributeUsageStats();
    void merge(const search::AddressSpaceUsage &usage,
               const vespalib::string &attributeName,
               const vespalib::string &subDbName);

    const AddressSpaceUsageStats& max_address_space_usage() const { return _max_usage; }

    bool operator==(const AttributeUsageStats& rhs) const {
        return (_max_usage == rhs._max_usage);
    }
};

std::ostream& operator<<(std::ostream& out, const AttributeUsageStats& rhs);

} // namespace proton
